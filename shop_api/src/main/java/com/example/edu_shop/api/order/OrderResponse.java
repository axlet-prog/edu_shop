package com.example.edu_shop.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.edu_shop.order.Order;
import com.example.edu_shop.order.OrderStatus;

public record OrderResponse(
		long id,
		BigDecimal amount,
		int itemsCount,
		OrderStatus status,
		Instant createdAt,
		Instant processedAt) {

	static OrderResponse from(Order order) {
		return new OrderResponse(order.id(), order.amount(), order.itemsCount(), order.status(),
				order.createdAt(), order.processedAt());
	}

}
