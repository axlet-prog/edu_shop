package com.example.edu_shop;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.example.edu_shop.messaging.OrderMessaging;
import com.example.edu_shop.order.Order;
import com.example.edu_shop.order.OrderRepository;
import com.example.edu_shop.order.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API целиком на настоящих PostgreSQL, RabbitMQ и Redis: миграции Liquibase, SQL-запросы и сообщение для Worker.
 * Контейнеры поднимает Testcontainers, поэтому для запуска нужен Docker.
 * Здесь контейнеры static, то есть общие для всех тестов класса: Spring-контекст поднимается один раз,
 * а независимость тестов обеспечивает очистка данных в {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(parallel = true)
class OrderApiIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-alpine");

	@Container
	@ServiceConnection(name = "redis")
	static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private OrderRepository orders;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@BeforeEach
	void cleanUp() {
		orders.deleteAll();
		amqpAdmin.purgeQueue(OrderMessaging.QUEUE, false);
	}

	@Test
	void healthReturnsUp() throws Exception {
		mvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void createdOrderIsStoredAndPublishedForWorker() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"amount": 1500.00, "items_count": 3}
				"""))
				.andExpect(status().isCreated());

		Order order = orders.findAll().getFirst();
		assertThat(order.status()).isEqualTo(OrderStatus.NEW);
		assertThat(order.amount()).isEqualByComparingTo("1500.00");

		Message message = rabbitTemplate.receive(OrderMessaging.QUEUE, 5_000);
		assertThat(message).isNotNull();
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
				.isEqualTo("{\"order_id\":%d}".formatted(order.id()));
	}

	@Test
	void statsCountOrdersByStatus() throws Exception {
		Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
		Instant processedAt = Instant.parse("2026-01-01T10:00:02Z");
		orders.save(Order.newOrder(new BigDecimal("10.00"), 1, createdAt));
		Order processing = orders.save(Order.newOrder(new BigDecimal("20.00"), 2, createdAt));
		Order done = orders.save(Order.newOrder(new BigDecimal("30.00"), 3, createdAt));
		// Так статусы меняет Worker
		assertThat(orders.markProcessing(processing.id())).isTrue();
		assertThat(orders.markProcessing(done.id())).isTrue();
		orders.markDone(done.id(), processedAt);
		assertThat(orders.markProcessing(done.id())).isFalse();

		mvc.perform(get("/internal/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orders_new").value(1))
				.andExpect(jsonPath("$.orders_processing").value(1))
				.andExpect(jsonPath("$.orders_done").value(1))
				.andExpect(jsonPath("$.last_processed_at").value(processedAt.toString()));
	}

	@Test
	void metricsEndpointExposesHttpAndOrderMetricsForPrometheus() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"amount": 1500.00, "items_count": 3}
				"""))
				.andExpect(status().isCreated());

		mvc.perform(get("/metrics"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("# TYPE shop_orders_created_total counter")))
				.andExpect(content().string(containsString(
						"http_requests_total{method=\"POST\",route=\"/orders\",status=\"201\"}")))
				.andExpect(content().string(containsString("# TYPE http_request_duration_seconds histogram")))
				// Встроенная метрика Spring отключена, чтобы HTTP-метрики не дублировались
				.andExpect(content().string(not(containsString("http_server_requests"))))
				// Метрики RabbitMQ отдаёт сам брокер, клиентские метрики с теми же именами отключены
				.andExpect(content().string(not(containsString("rabbitmq_connections"))));
	}

}
