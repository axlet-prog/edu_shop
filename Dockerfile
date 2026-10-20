# syntax=docker/dockerfile:1

# Один Dockerfile для всех своих компонентов. Какой модуль собрать, задаёт аргумент MODULE:
# shop_api, shop_worker или shop_exporter. Аргумент PORT — порт для EXPOSE (по умолчанию 8080). Например:
#   docker build --build-arg MODULE=shop_api --build-arg PORT=8080 -t edu-shop/api .
#
# Стадия сборки копирует файлы от редко меняющихся к часто меняющимся, поэтому при изменении только
# исходного кода слои с Gradle и зависимостями берутся из кэша:
#   1. Gradle Wrapper — меняется только при обновлении Gradle;
#   2. сборочные скрипты и gradle.lockfile — меняются при изменении зависимостей;
#   3. исходный код — меняется почти в каждом коммите.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# 1. Дистрибутив Gradle (около 140 МБ) скачивается в отдельный слой
COPY gradlew ./
COPY gradle/wrapper/ gradle/wrapper/
RUN ./gradlew --version --no-daemon

# 2. Зависимости скачиваются строго по gradle.lockfile, той же задачей, что и в шаге CI «Install dependencies».
# Кэш Gradle остаётся в слое образа, а не в RUN --mount=type=cache: кэш-маунты BuildKit не попадают
# в кэш GitHub Actions (type=gha), и в CI зависимости скачивались бы заново при каждой сборке
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/
COPY shop_common/build.gradle.kts shop_common/gradle.lockfile shop_common/
COPY shop_api/build.gradle.kts shop_api/gradle.lockfile shop_api/
COPY shop_worker/build.gradle.kts shop_worker/gradle.lockfile shop_worker/
COPY shop_exporter/build.gradle.kts shop_exporter/gradle.lockfile shop_exporter/
RUN ./gradlew resolveDependencies --no-daemon --console=plain

# 3. Исходный код. MODULE объявлен только здесь: аргумент влияет на кэш всех следующих RUN,
# поэтому слои выше одинаковы для всех модулей. Копируется только src/main модуля и shop_common,
# так что изменение тестов или другого модуля не пересобирает образ
ARG MODULE
RUN test -n "$MODULE" || (echo "Build argument MODULE is required: shop_api, shop_worker or shop_exporter" && exit 1)
COPY shop_common/src/main/ shop_common/src/main/
COPY ${MODULE}/src/main/ ${MODULE}/src/main/
# --offline: все зависимости уже лежат в слое из шага 2, сборка не ходит в сеть
RUN ./gradlew ":${MODULE}:bootJar" --offline --no-daemon --console=plain \
    && mkdir /app \
    && cp "${MODULE}/build/libs/${MODULE}.jar" /app/app.jar

# Spring Boot раскладывает jar по слоям: сторонние зависимости, загрузчик Spring Boot и код приложения
# (вместе с shop_common). Зависимости меняются редко, поэтому в рабочем образе это отдельный слой:
# при новом коммите в реестр загружается только небольшой слой с кодом
RUN java -Djarmode=tools -jar /app/app.jar extract --layers --destination /app/extracted


# Рабочий образ: только JRE и приложение, без JDK, Gradle, исходников и сборочных зависимостей.
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
USER app
ARG PORT=8080
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
