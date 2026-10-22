package com.example.edu_shop.worker;

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
		log.info("Order {} processing started", orderId);

		// Имитация обработки
		Thread.sleep(properties.processingDelay());

		orderRepository.markDone(orderId, Instant.now().truncatedTo(ChronoUnit.MICROS));
		processingDuration.observe(Unit.nanosToSeconds(System.nanoTime() - start));
		log.info("Order {} done", orderId);
	}

}
