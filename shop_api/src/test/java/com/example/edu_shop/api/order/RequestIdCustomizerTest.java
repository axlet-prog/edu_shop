package com.example.edu_shop.api.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.edu_shop.messaging.OrderMessaging;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdCustomizerTest {

	private final RabbitTemplate template = new RabbitTemplate();

	RequestIdCustomizerTest() {
		new RequestIdCustomizer().requestIdHeaderCustomizer().customize(template);
	}

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void copiesRequestIdFromMdcToMessageHeader() {
		MDC.put(OrderMessaging.REQUEST_ID_LOG_KEY, "test-1");

		Message message = beforePublish(new Message(new byte[0]));

		assertThat(message.getMessageProperties().getHeaders())
				.containsEntry(OrderMessaging.REQUEST_ID_HEADER, "test-1");
	}

	@Test
	void sendsMessageWithoutHeaderOutsideHttpRequest() {
		Message message = beforePublish(new Message(new byte[0]));

		assertThat(message.getMessageProperties().getHeaders()).doesNotContainKey(OrderMessaging.REQUEST_ID_HEADER);
	}

	/** Прогоняет сообщение через те же обработчики, что RabbitTemplate вызывает перед отправкой. */
	private Message beforePublish(Message message) {
		Message processed = message;
		for (MessagePostProcessor processor : template.getBeforePublishPostProcessors()) {
			processed = processor.postProcessMessage(processed);
		}
		return processed;
	}

}
