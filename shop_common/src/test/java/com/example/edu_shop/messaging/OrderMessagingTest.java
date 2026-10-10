package com.example.edu_shop.messaging;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMessagingTest {

	private final OrderMessaging messaging = new OrderMessaging();

	private final MessageConverter converter = messaging.jsonMessageConverter();

	@Test
	void queueIsBoundToExchangeByRoutingKey() {
		Binding binding = messaging.ordersBinding(messaging.ordersQueue(), messaging.ordersExchange());

		assertThat(binding.getExchange()).isEqualTo(OrderMessaging.EXCHANGE);
		assertThat(binding.getDestination()).isEqualTo(OrderMessaging.QUEUE);
		assertThat(binding.getRoutingKey()).isEqualTo(OrderMessaging.ROUTING_KEY);
	}

	@Test
	void failedMessagesGoToDeadLetterQueue() {
		Queue queue = messaging.ordersQueue();
		Queue deadLetterQueue = messaging.ordersDeadLetterQueue();

		assertThat(queue.isDurable()).isTrue();
		assertThat(deadLetterQueue.isDurable()).isTrue();
		assertThat(queue.getArguments())
				.containsEntry("x-dead-letter-exchange", "")
				.containsEntry("x-dead-letter-routing-key", deadLetterQueue.getName());
	}

	@Test
	void messageIsSerializedAsJsonWithOrderId() {
		Message message = converter.toMessage(new OrderCreatedMessage(42), new MessageProperties());

		assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"order_id\":42}");
	}

	@Test
	void messageIsDeserializedIntoListenerArgumentType() {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		// Так тип параметра метода @RabbitListener передаётся конвертеру в Worker
		properties.setInferredArgumentType(OrderCreatedMessage.class);
		Message message = new Message("{\"order_id\":42}".getBytes(StandardCharsets.UTF_8), properties);

		assertThat(converter.fromMessage(message)).isEqualTo(new OrderCreatedMessage(42));
	}

}
