# edu_shop

Учебный стенд для лабораторных по DevOps: небольшой сервис обработки заказов.

```
Клиент → HTTP → API ──→ PostgreSQL
                 ├────→ Redis (кэш счётчика посещений)
                 └────→ RabbitMQ ──→ Worker ──→ PostgreSQL
```

1. Клиент отправляет `POST /orders`, и API сохраняет заказ в PostgreSQL со статусом `NEW`.
2. API публикует в RabbitMQ сообщение `{"order_id": 42}`.
3. Worker получает сообщение и переводит заказ в `PROCESSING`. Затем он ждёт `PROCESSING_DELAY_MS`
   и переводит заказ в `DONE`, заполняя `processed_at`.

Стек: Java 25, Spring Boot 4, Spring Data JDBC, Liquibase, PostgreSQL, RabbitMQ, Redis.
Мониторинг: Prometheus, VictoriaMetrics, node_exporter, cAdvisor, postgres_exporter, rabbitmq_prometheus
и собственный exporter (см. [Метрики](#метрики)).

## Модули

| Модуль        | Что внутри                                                                     |
|---------------|--------------------------------------------------------------------------------|
| `shop_common` | Сущность `Order`, `OrderRepository`, топология RabbitMQ и формат сообщения      |
| `shop_api`    | HTTP API и миграции Liquibase (`shop_api/src/main/resources/db/changelog`)      |
| `shop_worker` | Фоновый обработчик очереди. Пользовательских HTTP-ручек нет, только `/metrics`  |
| `shop_exporter` | Собственный exporter: переводит JSON из `GET /internal/stats` в Prometheus-метрики |

Схему БД создаёт только API (Liquibase при старте), поэтому Worker нужно запускать после API.

## Запуск стенда в Docker

`docker-compose.yml` поднимает всё сразу: API, Worker, exporter, PostgreSQL, RabbitMQ, Redis и мониторинг.
Образы своих компонентов собираются из одного [`Dockerfile`](Dockerfile), модуль задаёт аргумент `MODULE`
(см. [Docker-образы](#docker-образы)).

```bash
docker compose up -d --build
docker compose ps          # api и worker стартуют после healthcheck PostgreSQL, RabbitMQ, Redis и API
docker compose down        # остановить; с флагом -v удалятся и данные
```

| Адрес                           | Что там                                          |
|---------------------------------|--------------------------------------------------|
| http://localhost:8080           | API                                              |
| http://localhost:8080/metrics   | Метрики API                                      |
| http://localhost:9101/metrics   | Метрики Worker                                   |
| http://localhost:9102/metrics   | Метрики собственного exporter                    |
| http://localhost:9090/targets   | Prometheus: состояние всех целей                 |
| http://localhost:8428/vmui      | VictoriaMetrics: те же метрики через `remote_write` |
| http://localhost:15672          | RabbitMQ Management (`shop` / `shop`)            |
| http://localhost:8081           | cAdvisor                                         |

Первая сборка образов занимает несколько минут: Gradle скачивает дистрибутив и зависимости.
Дальше, пока не меняются сборочные скрипты и `gradle.lockfile`, они берутся из кэша слоёв.

## Запуск локально

```bash
docker run -d --name shop-postgres -p 5432:5432 \
  -e POSTGRES_DB=shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop postgres:17-alpine
docker run -d --name shop-rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=shop -e RABBITMQ_DEFAULT_PASS=shop rabbitmq:4-management-alpine
docker run -d --name shop-redis -p 6379:6379 redis:7-alpine

./gradlew :shop_api:bootRun
./gradlew :shop_worker:bootRun     # в другом терминале
./gradlew :shop_exporter:bootRun   # по желанию, в третьем
```

Сборка исполняемых jar: `./gradlew assemble`. Результат: `shop_api.jar`, `shop_worker.jar` и `shop_exporter.jar`
в `*/build/libs/`.
`./gradlew build` дополнительно запускает линтер, unit- и интеграционные тесты (см. [Тесты](#тесты)).

## Тесты

Тесты разделены на два уровня, физически в разных каталогах:

| Уровень        | Каталог                     | Команда                                                  | Что нужно |
|----------------|-----------------------------|----------------------------------------------------------|-----------|
| Unit           | `*/src/test`                | `./gradlew test jacocoTestCoverageVerification`          | Ничего, Docker не нужен |
| Интеграционные | `shop_api/src/integrationTest` | `./gradlew integrationTest jacocoIntegrationTestCoverageVerification` | Запущенный Docker |

Интеграционные тесты поднимают настоящие PostgreSQL, Redis и RabbitMQ через Testcontainers
на случайных портах и удаляют контейнеры после теста, даже если он упал:

- `OrderApiIntegrationTest` — API целиком: заказы, сообщение для Worker, `/internal/stats`, `/metrics`.
  Контейнеры общие на класс, данные очищаются перед каждым тестом;
- `VisitsDatabaseIntegrationTest`, `VisitsCacheIntegrationTest` — `GET /visits`. Для каждого теста
  поднимаются новые PostgreSQL и Redis и новое приложение (общая часть в `VisitsIntegrationTestSupport`).

Тестовые классы выполняются параллельно в потоках JUnit, тесты внутри класса — последовательно.
Без параллельности (например, чтобы сравнить время): `./gradlew integrationTest -PintegrationTest.parallel=false`.

Порог покрытия JaCoCo (80% строк и ветвлений) разделён между уровнями: код, который работает с базой, Redis
и сборкой Spring-контекста, проверяется интеграционными тестами и считается только в их пороге,
остальной код — в пороге unit-тестов. Список классов — `integrationTestedClasses` в
[`shop_api/build.gradle.kts`](shop_api/build.gradle.kts).

Отчёты: `*/build/reports/tests/{test,integrationTest}/index.html` и
`*/build/reports/jacoco/{test,integrationTest}/`.

## CI

Пайплайн GitHub Actions (`.github/workflows/ci.yml`) запускается на push в `main` и на Pull Request в `main`.
Job-ы связаны через `needs`, поэтому сбой любого из них останавливает все следующие (они получают статус `skipped`):

```
ci → integration-tests → image (api, worker, exporter) → promote-to-staging → update-manifest
                                                          └─────── только push в main ───────┘
```

| Job                 | Что делает                                                                                     |
|---------------------|------------------------------------------------------------------------------------------------|
| `ci`                | Зависимости строго по `gradle.lockfile`, Checkstyle, unit-тесты с порогом покрытия, сборка jar |
| `integration-tests` | Интеграционные тесты на Testcontainers с порогом покрытия и проверка, что контейнеры удалены  |
| `image`             | Сборка образа, Trivy, SBOM, публикация в GHCR (см. [Docker-образы](#docker-образы))           |
| `promote-to-staging`| Копирует опубликованный образ в staging через `skopeo copy`, без пересборки                   |
| `update-manifest`   | Меняет тег образов в `k8s-manifests/staging/deployment.yaml` и коммитит в `main` (GitOps)     |

Шаги `ci`:

| Шаг                    | Команда                                         | Что делает                                                        |
|------------------------|-------------------------------------------------|-------------------------------------------------------------------|
| Установка зависимостей | `./gradlew resolveDependencies`                 | Скачивает зависимости строго по `gradle.lockfile`                 |
| Линтер                 | `./gradlew checkstyleMain checkstyleTest checkstyleIntegrationTest` | Проверяет код по правилам `config/checkstyle/checkstyle.xml` |
| Unit-тесты с покрытием | `./gradlew test jacocoTestCoverageVerification` | JUnit + JaCoCo: покрытие строк и ветвлений в каждом модуле ≥ 80%  |
| Сборка                 | `./gradlew assemble`                            | Собирает исполняемые jar API, Worker и exporter                   |

Те же команды работают локально. В CI отчёты сохраняются в артефакты `test-reports` и `integration-test-reports`.

Версии зависимостей зафиксированы в `gradle.lockfile` каждого модуля. После изменения зависимостей
обновите lock-файлы и закоммитьте их вместе с изменением:

```bash
./gradlew resolveDependencies --write-locks
```

## Docker-образы

[`Dockerfile`](Dockerfile) многоступенчатый:

- стадия `build` (`eclipse-temurin:25-jdk`) собирает `bootJar`. Файлы копируются от редко меняющихся
  к часто меняющимся: Gradle Wrapper, затем сборочные скрипты с `gradle.lockfile` и скачивание зависимостей,
  и только потом `src/main`. Поэтому при изменении кода слой с зависимостями берётся из кэша;
- рабочая стадия (`eclipse-temurin:25-jre-alpine`) содержит только JRE и приложение, разложенное по слоям
  Spring Boot: сторонние зависимости отдельно, код приложения отдельно. Процесс запускается
  от непривилегированного пользователя `app`.

```bash
docker build --build-arg MODULE=shop_api --build-arg PORT=8080 -t edu-shop/api:local .
docker run --rm -p 8080:8080 -e DB_HOST=... -e RABBITMQ_HOST=... edu-shop/api:local
```

`Dockerfile.singlestage` — однослойный вариант, только для сравнения размера (1.38 ГБ против 268 МБ у API).

После интеграционных тестов пайплайн запускает job `Image` для каждого сервиса (api, worker, exporter):

| Шаг              | Что делает                                                                                 |
|------------------|--------------------------------------------------------------------------------------------|
| Сборка образа    | `docker/build-push-action` с кэшем слоёв в кэше GitHub Actions (`type=gha`, свой `scope` у сервиса) |
| Отчёт Trivy      | Все уязвимости всех уровней, артефакт `trivy-report-<сервис>`                             |
| SBOM             | CycloneDX (`trivy image --format cyclonedx`), артефакт `sbom-<сервис>`                    |
| Quality Gate     | `trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1`                      |
| Публикация       | Только из `main` и только после Quality Gate: `docker push` в GHCR                        |

Образы публикуются как `ghcr.io/axlet-prog/edu_shop/<сервис>` с двумя тегами на один digest:
короткий хэш коммита (`${GITHUB_SHA::7}`, например `3ede026`) и номер сборки (`build-42`). Тега `latest` нет:
каждый тег однозначно указывает на коммит или запуск пайплайна.

Проверить образ локально так же, как в CI:

```bash
trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 edu-shop/api:local
trivy image --format cyclonedx --output sbom-api.cdx.json edu-shop/api:local
```

Если Quality Gate находит уязвимость в зависимости из BOM Spring Boot, а новой версии Spring Boot ещё нет,
версию можно поднять свойством в `gradle.properties` (например, `tomcat.version`) и обновить lock-файлы.

## Продвижение в staging

Staging получает только тот образ, который уже прошёл все проверки и был опубликован в job `image`
(принцип Build Once, Deploy Many). На push в `main`:

1. `promote-to-staging` копирует `ghcr.io/axlet-prog/edu_shop/<сервис>:<короткий SHA>` в
   `ghcr.io/axlet-prog/edu_shop-staging/<сервис>:<тот же SHA>` через `skopeo copy --preserve-digests`
   и сверяет digest источника и копии.
2. `update-manifest` меняет тег образов в [`k8s-manifests/staging/deployment.yaml`](k8s-manifests/staging/deployment.yaml)
   через `yq`, проверяет, что все образы указывают на новый тег, и коммитит изменение в `main`
   (коммит `Deploy <SHA> to staging` от `github-actions[bot]`).

Пайплайн к кластеру не подключается (GitOps, модель pull): изменения применяет ArgoCD или вручную
`kubectl apply -f k8s-manifests/staging/`. Проверить образ в staging:

```bash
skopeo inspect docker://ghcr.io/axlet-prog/edu_shop-staging/api:<короткий SHA>
git log -p -1 -- k8s-manifests/staging/deployment.yaml
```

## HTTP API (порт 8080)

| Ручка                  | Описание                                                    |
|------------------------|-------------------------------------------------------------|
| `POST /orders`         | Создать заказ: `{"amount": 1500.00, "items_count": 3}`      |
| `GET /orders/{id}`     | Получить один заказ                                         |
| `GET /orders`          | Получить все заказы                                         |
| `GET /health`          | Проверить, что API запущено                                 |
| `GET /visits`          | Счётчик посещений: запись в PostgreSQL, результат кэшируется в Redis на 10 секунд (`source`: `db` или `cache`) |
| `GET /internal/stats`  | Количество заказов по статусам и `last_processed_at` в JSON |
| `GET /metrics`         | Метрики API в формате Prometheus                            |

```bash
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"amount": 1500.00, "items_count": 3}'
curl localhost:8080/orders/1
curl localhost:8080/internal/stats
```

Ошибки возвращаются в формате [Problem Details](https://www.rfc-editor.org/rfc/rfc9457): ответ 400 при невалидном
запросе и 404, если заказ не найден.

Postman-коллекция со всеми ручками стенда, тестами и PromQL-запросами:
[`docs/postman/edu_shop.postman_collection.json`](docs/postman/edu_shop.postman_collection.json)
(Import → File). Без Postman её можно запустить через Newman:

```bash
docker run --rm --network host -v "$PWD/docs/postman:/etc/newman:ro" \
  postman/newman:6.1.3-alpine run edu_shop.postman_collection.json
```

## RabbitMQ

| Объект                  | Назначение                                                        |
|-------------------------|-------------------------------------------------------------------|
| exchange `orders`       | direct exchange, в который API публикует события                  |
| queue `orders.processing` | очередь Worker-а, routing key `order.created`                   |
| queue `orders.processing.dlq` | сообщения, которые не удалось обработать после 3 попыток    |

Worker берёт сообщения по одному (`prefetch: 1`). Если Worker не успевает, необработанные заказы копятся
в очереди как `messages_ready`. Если Worker упал посреди обработки, RabbitMQ доставит сообщение повторно,
и заказ будет обработан заново.

## Метрики

| Компонент            | Ручка            | Метрики                                                                          |
|----------------------|------------------|----------------------------------------------------------------------------------|
| API                  | `:8080/metrics`  | `http_requests_total`, `http_request_duration_seconds`, `shop_orders_created_total` |
| Worker               | `:9101/metrics`  | `shop_order_processing_duration_seconds`                                         |
| Собственный exporter | `:9102/metrics`  | `shop_stats_orders_new`, `shop_stats_orders_processing`, `shop_stats_orders_done`, `shop_stats_seconds_since_last_processed`, `shop_stats_up` |

Метрики API и Worker считает их код (клиент Prometheus `prometheus-metrics-core`), метрики JVM добавляет
Spring Boot Actuator. Exporter при каждом запросе Prometheus вызывает `GET /internal/stats` у API.
Prometheus (конфигурация в [`monitoring/prometheus/prometheus.yml`](monitoring/prometheus/prometheus.yml))
опрашивает эти ручки и готовые exporter-ы инфраструктуры и копирует всё в VictoriaMetrics через `remote_write`.

Описание всех метрик, PromQL-запросы и сравнение подходов: [docs/metrics.md](docs/metrics.md).

## Переменные окружения

| Переменная              | По умолчанию | Где используется |
|-------------------------|--------------|------------------|
| `DB_HOST` / `DB_PORT`   | `localhost` / `5432` | API, Worker |
| `DB_NAME`               | `shop`       | API, Worker      |
| `DB_USER` / `DB_PASSWORD` | `shop` / `shop` | API, Worker |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | API, Worker |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `shop` / `shop` | API, Worker |
| `API_PORT`              | `8080`       | API              |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | API |
| `VISITS_CACHE_TTL`      | `10s`        | API: сколько счётчик посещений живёт в Redis |
| `PROCESSING_DELAY_MS`   | `2000`       | Worker: сколько миллисекунд обрабатывается один заказ |
| `METRICS_PORT`          | `9101`       | Worker: порт ручки `/metrics` |
| `EXPORTER_PORT`         | `9102`       | Exporter: порт ручки `/metrics` |
| `SHOP_API_URL`          | `http://localhost:8080` | Exporter: адрес API, у которого он запрашивает `/internal/stats` |
