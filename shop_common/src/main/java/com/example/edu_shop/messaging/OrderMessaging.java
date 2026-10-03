package com.example.edu_shop.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Топология RabbitMQ для заказов. Её объявляют и API, и Worker, поэтому неважно,
 * какой из них стартует первым.
 */
@Configuration(proxyBeanMethods = false)
public class OrderMessaging {

	public static final String EXCHANGE = "orders";

	public static final String ROUTING_KEY = "order.created";

	public static final String QUEUE = "orders.processing";

	/** Сюда попадают сообщения, которые Worker не смог обработать после всех повторов. */
	public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

	@Bean
	DirectExchange ordersExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	Queue ordersQueue() {
		return QueueBuilder.durable(QUEUE)
				.deadLetterExchange("")
				.deadLetterRoutingKey(DEAD_LETTER_QUEUE)
				.build();
	}

	@Bean
	Queue ordersDeadLetterQueue() {
		return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding ordersBinding(Queue ordersQueue, DirectExchange ordersExchange) {
		return BindingBuilder.bind(ordersQueue).to(ordersExchange).with(ROUTING_KEY);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

}
