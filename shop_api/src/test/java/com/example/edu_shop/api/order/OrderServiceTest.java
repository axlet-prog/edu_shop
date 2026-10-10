package com.example.edu_shop.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.example.edu_shop.api.metrics.MetricsText;
import com.example.edu_shop.messaging.OrderCreatedMessage;
import com.example.edu_shop.messaging.OrderMessaging;
import com.example.edu_shop.order.Order;
import com.example.edu_shop.order.OrderRepository;
import com.example.edu_shop.order.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	private static final Order ORDER = new Order(42L, new BigDecimal("1500.00"), 3, OrderStatus.NEW,
			Instant.parse("2026-01-01T10:00:00Z"), null);

	@Mock
	private OrderRepository orders;

	@Mock
	private RabbitTemplate rabbitTemplate;

	private final PrometheusRegistry registry = new PrometheusRegistry();

	private OrderService orderService;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(orders, rabbitTemplate, registry);
	}

	@Test
	void createSavesNewOrderAndPublishesMessage() {
		given(orders.save(any(Order.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 42L));

		Order created = orderService.create(new BigDecimal("1500"), 3);

		ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
		verify(orders).save(saved.capture());
		// equals у BigDecimal учитывает scale: сумма должна быть приведена к NUMERIC(12, 2)
		assertThat(saved.getValue().amount()).isEqualTo(new BigDecimal("1500.00"));
		assertThat(saved.getValue().itemsCount()).isEqualTo(3);
		assertThat(saved.getValue().status()).isEqualTo(OrderStatus.NEW);
		assertThat(saved.getValue().createdAt()).isEqualTo(saved.getValue().createdAt().truncatedTo(ChronoUnit.MICROS));
		verify(rabbitTemplate).convertAndSend(OrderMessaging.EXCHANGE, OrderMessaging.ROUTING_KEY,
				new OrderCreatedMessage(42L));
		assertThat(created.id()).isEqualTo(42L);
		assertThat(MetricsText.scrape(registry)).contains("shop_orders_created_total 1.0");
	}

	@Test
	void createDoesNotCountOrderWhenPublishFails() {
		given(orders.save(any(Order.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 42L));
		willThrow(new AmqpConnectException(new RuntimeException("RabbitMQ is down")))
				.given(rabbitTemplate).convertAndSend(eq(OrderMessaging.EXCHANGE), eq(OrderMessaging.ROUTING_KEY),
						any(OrderCreatedMessage.class));

		assertThatExceptionOfType(AmqpConnectException.class)
				.isThrownBy(() -> orderService.create(new BigDecimal("1500"), 3));
		assertThat(MetricsText.scrape(registry)).contains("shop_orders_created_total 0.0");
	}

	@Test
	void getReturnsExistingOrder() {
		given(orders.findById(42L)).willReturn(Optional.of(ORDER));

		assertThat(orderService.get(42L)).isEqualTo(ORDER);
	}

	@Test
	void getThrowsNotFoundForUnknownOrder() {
		given(orders.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(OrderNotFoundException.class)
				.isThrownBy(() -> orderService.get(99L))
				.satisfies(ex -> {
					assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(ex.getBody().getDetail()).isEqualTo("Order 99 not found");
				});
	}

	@Test
	void findAllReturnsOrdersSortedById() {
		given(orders.findAll(Sort.by("id"))).willReturn(List.of(ORDER));

		assertThat(orderService.findAll()).containsExactly(ORDER);
	}

	private static Order withId(Order order, long id) {
		return new Order(id, order.amount(), order.itemsCount(), order.status(), order.createdAt(),
				order.processedAt());
	}

}
