package com.example.edu_shop.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

	@Test
	void newOrderIsNotSavedAndNotProcessed() {
		Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");

		Order order = Order.newOrder(new BigDecimal("1500.00"), 3, createdAt);

		assertThat(order.id()).isNull();
		assertThat(order.status()).isEqualTo(OrderStatus.NEW);
		assertThat(order.amount()).isEqualByComparingTo("1500.00");
		assertThat(order.itemsCount()).isEqualTo(3);
		assertThat(order.createdAt()).isEqualTo(createdAt);
		assertThat(order.processedAt()).isNull();
	}

}
