package com.example.edu_shop.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
public record Order(
		@Id Long id,
		BigDecimal amount,
		int itemsCount,
		OrderStatus status,
		Instant createdAt,
		Instant processedAt) {

	public static Order newOrder(BigDecimal amount, int itemsCount, Instant createdAt) {
		return new Order(null, amount, itemsCount, OrderStatus.NEW, createdAt, null);
	}

}
