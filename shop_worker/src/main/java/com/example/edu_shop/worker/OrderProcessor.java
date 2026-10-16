package com.example.edu_shop.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.edu_shop.messaging.OrderCreatedMessage;
import com.example.edu_shop.messaging.OrderMessaging;
import com.example.edu_shop.order.OrderRepository;

@Component
public class OrderProcessor {

	private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

	private final OrderRepository orderRepository;

	private final WorkerProperties properties;

	private final Histogram processingDuration;

	public OrderProcessor(OrderRepository orderRepository, WorkerProperties properties, PrometheusRegistry registry) {
		this.orderRepository = orderRepository;
		this.properties = properties;
		this.processingDuration = Histogram.builder()
				.name("shop_order_processing_duration_seconds")
				.help("Time from receiving a message from RabbitMQ until the order is DONE")
				// Обработка длится не меньше PROCESSING_DELAY_MIN_MS (по умолчанию от 1 до 3 с), поэтому бакеты
				// крупнее, чем у HTTP-запросов: от 100 мс до минуты
				.classicUpperBounds(0.1, 0.25, 0.5, 1, 2, 3, 5, 10, 20, 30, 60)
				.classicOnly()
				.register(registry);
	}

	@RabbitListener(queues = OrderMessaging.QUEUE)
	public void process(OrderCreatedMessage message) throws InterruptedException {
		long start = System.nanoTime();
		long orderId = message.orderId();
		if (!orderRepository.markProcessing(orderId)) {
			log.warn("Order {} not found or already done, skipping", orderId);
			return;
		}
		// Имитация обработки: каждый заказ обрабатывается случайное время в заданных пределах
		Duration delay = properties.randomProcessingDelay();
		log.info("Order {} processing started, delay {} ms", orderId, delay.toMillis());
		Thread.sleep(delay);

		orderRepository.markDone(orderId, Instant.now().truncatedTo(ChronoUnit.MICROS));
		processingDuration.observe(Unit.nanosToSeconds(System.nanoTime() - start));
		log.info("Order {} done", orderId);
	}

}
