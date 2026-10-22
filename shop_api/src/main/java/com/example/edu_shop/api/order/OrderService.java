package com.example.edu_shop.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
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

	private final OrderRepository orderRepository;

	private final RabbitTemplate rabbitTemplate;

	private final Counter ordersCreatedCounter;

	public OrderService(OrderRepository orderRepository, RabbitTemplate rabbitTemplate, PrometheusRegistry registry) {
		this.orderRepository = orderRepository;
		this.rabbitTemplate = rabbitTemplate;
		this.ordersCreatedCounter = Counter.builder()
				.name("shop_orders_created_total")
				.help("Orders created since API start")
				.register(registry);
	}

	public Order create(BigDecimal amount, int itemsCount) {
		BigDecimal normalizedAmount = amount.setScale(2);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Order order = orderRepository.save(Order.newOrder(normalizedAmount, itemsCount, now));
		rabbitTemplate.convertAndSend(
				OrderMessaging.EXCHANGE,
				OrderMessaging.ROUTING_KEY,
				new OrderCreatedMessage(order.id()));
		ordersCreatedCounter.inc();
		log.info("Order {} created", order.id());
		return order;
	}

	public Order get(long id) {
		return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	public List<Order> findAll() {
		return orderRepository.findAll(Sort.by("id"));
	}

}
