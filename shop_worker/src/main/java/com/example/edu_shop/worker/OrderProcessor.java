package com.example.edu_shop.worker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

	/**
	 * request_id приходит из API в заголовке сообщения и кладётся в MDC: так он попадает во все логи обработки.
	 * Заголовка может не быть (сообщение отправлено вне HTTP-запроса), тогда логи пишутся без request_id.
	 */
	@RabbitListener(queues = OrderMessaging.QUEUE)
	public void process(@Payload OrderCreatedMessage message,
			@Header(name = OrderMessaging.REQUEST_ID_HEADER, required = false) String requestId)
			throws InterruptedException {
		if (StringUtils.hasText(requestId)) {
			MDC.put(OrderMessaging.REQUEST_ID_LOG_KEY, requestId);
		}
		try {
			processOrder(message.orderId());
		} finally {
			// Поток слушателя переиспользуется: без очистки request_id попал бы в логи, не связанные с заказом
			MDC.remove(OrderMessaging.REQUEST_ID_LOG_KEY);
		}
	}

	private void processOrder(long orderId) throws InterruptedException {
		long start = System.nanoTime();
		log.atInfo()
				.addKeyValue("event", "message_received")
				.addKeyValue("order_id", orderId)
				.addKeyValue("queue", OrderMessaging.QUEUE)
				.log("Order {} received from RabbitMQ", orderId);

		log.atInfo()
				.addKeyValue("event", "processing_started")
				.addKeyValue("order_id", orderId)
				.log("Order {} processing started", orderId);
		if (!orderRepository.markProcessing(orderId)) {
			log.atWarn()
					.addKeyValue("event", "processing_skipped")
					.addKeyValue("order_id", orderId)
					.log("Order {} not found or already done, skipping", orderId);
			return;
		}

		// Имитация обработки
		Thread.sleep(properties.processingDelay());

		orderRepository.markDone(orderId, Instant.now().truncatedTo(ChronoUnit.MICROS));
		// Одно и то же время в метрике и в логе: гистограмма для массовой картины, duration_ms для конкретного заказа
		long elapsed = System.nanoTime() - start;
		processingDuration.observe(Unit.nanosToSeconds(elapsed));
		log.atInfo()
				.addKeyValue("event", "processing_finished")
				.addKeyValue("order_id", orderId)
				.addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(elapsed))
				.log("Order {} processing finished", orderId);
	}

}
