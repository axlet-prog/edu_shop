package com.example.edu_shop.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.edu_shop.messaging.OrderCreatedMessage;
import com.example.edu_shop.messaging.OrderMessaging;
import com.example.edu_shop.order.Order;
import com.example.edu_shop.order.OrderRepository;

@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final OrderRepository orders;

	private final RabbitTemplate rabbitTemplate;

	public OrderService(OrderRepository orders, RabbitTemplate rabbitTemplate) {
		this.orders = orders;
		this.rabbitTemplate = rabbitTemplate;
	}

	/**
	 * Сначала заказ сохраняется (и коммитится) в PostgreSQL, и только потом уходит сообщение в RabbitMQ,
	 * поэтому Worker всегда найдёт заказ в базе. Если RabbitMQ недоступен, запрос завершится ошибкой,
	 * а заказ останется в статусе NEW.
	 */
	public Order create(BigDecimal amount, int itemsCount) {
		// Приводим значения к тому виду, в котором их вернёт PostgreSQL: NUMERIC(12, 2) и микросекунды
		BigDecimal normalizedAmount = amount.setScale(2);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Order order = orders.save(Order.newOrder(normalizedAmount, itemsCount, now));
		rabbitTemplate.convertAndSend(OrderMessaging.EXCHANGE, OrderMessaging.ROUTING_KEY,
				new OrderCreatedMessage(order.id()));
		log.info("Order {} created", order.id());
		return order;
	}

	public Order get(long id) {
		return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	public List<Order> findAll() {
		return orders.findAll(Sort.by("id"));
	}

}
