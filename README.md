# edu_shop

Учебный стенд для лабораторных по DevOps: небольшой сервис обработки заказов.

```
Клиент → HTTP → API ──→ PostgreSQL
                 └────→ RabbitMQ ──→ Worker ──→ PostgreSQL
```

1. Клиент отправляет `POST /orders`, и API сохраняет заказ в PostgreSQL со статусом `NEW`.
2. API публикует в RabbitMQ сообщение `{"order_id": 42}`.
3. Worker получает сообщение и переводит заказ в `PROCESSING`. Затем он ждёт `PROCESSING_DELAY_MS`
   и переводит заказ в `DONE`, заполняя `processed_at`.

Стек: Java 25, Spring Boot 4, Spring Data JDBC, Liquibase, PostgreSQL, RabbitMQ.

## Модули

| Модуль        | Что внутри                                                                     |
|---------------|--------------------------------------------------------------------------------|
| `shop_common` | Сущность `Order`, `OrderRepository`, топология RabbitMQ и формат сообщения      |
| `shop_api`    | HTTP API и миграции Liquibase (`shop_api/src/main/resources/db/changelog`)      |
| `shop_worker` | Фоновый обработчик очереди, HTTP-запросы не принимает                           |

Схему БД создаёт только API (Liquibase при старте), поэтому Worker нужно запускать после API.

## Запуск локально

```bash
docker run -d --name shop-postgres -p 5432:5432 \
  -e POSTGRES_DB=shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop postgres:17-alpine
docker run -d --name shop-rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=shop -e RABBITMQ_DEFAULT_PASS=shop rabbitmq:4-management-alpine

./gradlew :shop_api:bootRun
./gradlew :shop_worker:bootRun   # в другом терминале
```

Сборка исполняемых jar: `./gradlew build`. Результат: `shop_api/build/libs/shop_api-*.jar` и `shop_worker/build/libs/shop_worker-*.jar`.

## HTTP API (порт 8080)

| Ручка                  | Описание                                                    |
|------------------------|-------------------------------------------------------------|
| `POST /orders`         | Создать заказ: `{"amount": 1500.00, "items_count": 3}`      |
| `GET /orders/{id}`     | Получить один заказ                                         |
| `GET /orders`          | Получить все заказы                                         |
| `GET /health`          | Проверить, что API запущено                                 |
| `GET /internal/stats`  | Количество заказов по статусам и `last_processed_at` в JSON |

```bash
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"amount": 1500.00, "items_count": 3}'
curl localhost:8080/orders/1
curl localhost:8080/internal/stats
```

Ошибки возвращаются в формате [Problem Details](https://www.rfc-editor.org/rfc/rfc9457): ответ 400 при невалидном
запросе и 404, если заказ не найден.

## RabbitMQ

| Объект                  | Назначение                                                        |
|-------------------------|-------------------------------------------------------------------|
| exchange `orders`       | direct exchange, в который API публикует события                  |
| queue `orders.processing` | очередь Worker-а, routing key `order.created`                   |
| queue `orders.processing.dlq` | сообщения, которые не удалось обработать после 3 попыток    |

Worker берёт сообщения по одному (`prefetch: 1`). Если Worker не успевает, необработанные заказы копятся
в очереди как `messages_ready`. Если Worker упал посреди обработки, RabbitMQ доставит сообщение повторно,
и заказ будет обработан заново.

## Переменные окружения

| Переменная              | По умолчанию | Где используется |
|-------------------------|--------------|------------------|
| `DB_HOST` / `DB_PORT`   | `localhost` / `5432` | API, Worker |
| `DB_NAME`               | `shop`       | API, Worker      |
| `DB_USER` / `DB_PASSWORD` | `shop` / `shop` | API, Worker |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | API, Worker |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `shop` / `shop` | API, Worker |
| `API_PORT`              | `8080`       | API              |
| `PROCESSING_DELAY_MS`   | `2000`       | Worker: сколько миллисекунд обрабатывается один заказ |
