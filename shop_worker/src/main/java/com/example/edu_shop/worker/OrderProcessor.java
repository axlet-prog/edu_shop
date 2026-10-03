package com.example.edu_shop.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

	private final OrderRepository orders;

	private final Duration processingDelay;

	public OrderProcessor(OrderRepository orders, WorkerProperties properties) {
		this.orders = orders;
		this.processingDelay = properties.processingDelay();
	}

	@RabbitListener(queues = OrderMessaging.QUEUE)
	public void process(OrderCreatedMessage message) throws InterruptedException {
		long orderId = message.orderId();
		if (!orders.markProcessing(orderId)) {
			log.warn("Order {} not found or already done, skipping", orderId);
			return;
		}
		log.info("Order {} processing started", orderId);

		// Имитация обработки
		Thread.sleep(processingDelay);

		orders.markDone(orderId, Instant.now().truncatedTo(ChronoUnit.MICROS));
		log.info("Order {} done", orderId);
	}

}
