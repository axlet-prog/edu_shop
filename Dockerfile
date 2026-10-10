# syntax=docker/dockerfile:1

# Один Dockerfile для всех своих компонентов. Какой модуль собрать, задаёт аргумент MODULE:
# shop_api, shop_worker или shop_exporter. Например:
#   docker build --build-arg MODULE=shop_api -t edu-shop/api .

FROM eclipse-temurin:25-jdk AS build
ARG MODULE
RUN test -n "$MODULE" || (echo "Build argument MODULE is required: shop_api, shop_worker or shop_exporter" && exit 1)
WORKDIR /src
COPY . .
# Кэш Gradle (дистрибутив и зависимости) сохраняется между сборками образа. sharing=locked: compose собирает
# образы параллельно, а блокировки Gradle не работают между контейнерами, поэтому кэш выдаётся сборкам по очереди
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew ":${MODULE}:bootJar" --no-daemon --console=plain \
    && cp "${MODULE}/build/libs/${MODULE}.jar" /app.jar

# В alpine-образе есть wget, им пользуется healthcheck в docker-compose.yml
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
