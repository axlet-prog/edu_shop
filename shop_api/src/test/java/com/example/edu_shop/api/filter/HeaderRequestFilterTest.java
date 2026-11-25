package com.example.edu_shop.api.filter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.edu_shop.messaging.OrderMessaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class HeaderRequestFilterTest {

	private final HeaderRequestFilter filter = new HeaderRequestFilter();

	private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final Logger logger = (Logger) LoggerFactory.getLogger(HeaderRequestFilter.class);

	/** Собирает события логгера фильтра. MDC фиксируется в момент записи, как это делает настоящий appender. */
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
		@Override
		protected void append(ILoggingEvent event) {
			event.prepareForDeferredProcessing();
			super.append(event);
		}
	};

	@BeforeEach
	void attachAppender() {
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void detachAppender() {
		logger.detachAppender(logs);
		MDC.clear();
	}

	@Test
	void generatesRequestIdWhenHeaderIsMissing() throws Exception {
		AtomicReference<String> requestIdInMdc = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> requestIdInMdc.set(currentRequestId()));

		String requestId = response.getHeader("X-Request-ID");
		assertThat(UUID.fromString(requestId)).hasToString(requestId);
		assertThat(requestIdInMdc).hasValue(requestId);
	}

	@Test
	void generatesRequestIdWhenHeaderIsBlank() throws Exception {
		request.addHeader("X-Request-ID", " ");

		filter.doFilter(request, response, (req, res) -> { });

		String requestId = response.getHeader("X-Request-ID");
		assertThat(UUID.fromString(requestId)).hasToString(requestId);
	}

	@Test
	void reusesRequestIdFromClientHeader() throws Exception {
		request.addHeader("X-Request-ID", "test-1");
		AtomicReference<String> requestIdInMdc = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> requestIdInMdc.set(currentRequestId()));

		assertThat(response.getHeader("X-Request-ID")).isEqualTo("test-1");
		assertThat(requestIdInMdc).hasValue("test-1");
	}

	@Test
	void logsStartAndFinishOfRequestWithSameRequestId() throws Exception {
		request.addHeader("X-Request-ID", "test-1");

		filter.doFilter(request, response, (req, res) -> response.setStatus(201));

		assertThat(logs.list).extracting(event -> keyValue(event, "event"))
				.containsExactly("request_started", "request_finished");
		assertThat(logs.list).extracting(event -> event.getMDCPropertyMap().get(OrderMessaging.REQUEST_ID_LOG_KEY))
				.containsOnly("test-1");
		ILoggingEvent finished = logs.list.get(1);
		assertThat(keyValue(finished, "method")).isEqualTo("POST");
		assertThat(keyValue(finished, "route")).isEqualTo("/orders");
		assertThat(keyValue(finished, "status")).isEqualTo(201);
		assertThat(keyValue(finished, "duration_ms")).isInstanceOf(Long.class);
	}

	@Test
	void removesRequestIdFromMdcAfterRequest() throws Exception {
		filter.doFilter(request, response, (req, res) -> { });

		// Поток Tomcat переиспользуется, поэтому request_id не должен остаться в MDC после ответа
		assertThat(currentRequestId()).isNull();
	}

	@Test
	void logsUnhandledExceptionAsServerErrorAndCleansMdc() {
		assertThatExceptionOfType(ServletException.class).isThrownBy(() -> filter.doFilter(request, response,
				(req, res) -> {
					throw new ServletException("RabbitMQ is down");
				}));

		// Tomcat выставит 500 уже после выхода из фильтра, поэтому статус в логе фильтр определяет сам
		assertThat(keyValue(logs.list.getLast(), "status")).isEqualTo(500);
		assertThat(currentRequestId()).isNull();
	}

	private static String currentRequestId() {
		return MDC.get(OrderMessaging.REQUEST_ID_LOG_KEY);
	}

	private static Object keyValue(ILoggingEvent event, String key) {
		return event.getKeyValuePairs().stream()
				.filter(pair -> pair.key.equals(key))
				.map(pair -> pair.value)
				.findFirst()
				.orElse(null);
	}

}
