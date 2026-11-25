package com.example.edu_shop.api.order;

import com.example.edu_shop.messaging.OrderMessaging;
import org.slf4j.MDC;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RequestIdCustomizer {

	@Bean
	public RabbitTemplateCustomizer requestIdHeaderCustomizer() {
		return (template) -> template.addBeforePublishPostProcessors(message -> {
					String requestId = MDC.get(OrderMessaging.REQUEST_ID_LOG_KEY);
					if (StringUtils.hasText(requestId)) {
						message.getMessageProperties().setHeader(OrderMessaging.REQUEST_ID_HEADER, requestId);
					}
					return message;
				}
		);
	}
}
