package com.example.edu_shop.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Сообщение, которое API отправляет в RabbitMQ после создания заказа: {@code {"order_id": 42}}.
 */
public record OrderCreatedMessage(@JsonProperty("order_id") long orderId) {
}
