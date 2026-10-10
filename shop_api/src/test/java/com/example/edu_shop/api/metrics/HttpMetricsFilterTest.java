package com.example.edu_shop.api.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class HttpMetricsFilterTest {

	private final PrometheusRegistry registry = new PrometheusRegistry();

	private final HttpMetricsFilter filter = new HttpMetricsFilter(registry);

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@Test
	void recordsRequestByRoutePatternAndStatus() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");

		filter.doFilter(request, response, (req, res) -> {
			// Так шаблон пути выставляет Spring MVC, когда находит обработчик
			req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
			response.setStatus(404);
		});

		String labels = "method=\"GET\",route=\"/orders/{id}\",status=\"404\"";
		assertThat(scrape()).contains(
				"http_requests_total{" + labels + "} 1.0",
				"http_request_duration_seconds_count{" + labels + "} 1",
				// Бакеты гистограммы нужны, чтобы в Prometheus считать p95/p99 через histogram_quantile
				"http_request_duration_seconds_bucket{" + labels + ",le=\"10.0\"} 1");
	}

	@Test
	void usesUnknownRouteWhenNoHandlerMatched() throws Exception {
		filter.doFilter(new MockHttpServletRequest("POST", "/unknown"), response, (req, res) -> { });

		assertThat(scrape()).contains("http_requests_total{method=\"POST\",route=\"UNKNOWN\",status=\"200\"} 1.0");
	}

	@Test
	void countsUnhandledExceptionAsServerError() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");

		assertThatExceptionOfType(ServletException.class).isThrownBy(() -> filter.doFilter(request, response,
				(req, res) -> {
					req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders");
					throw new ServletException("RabbitMQ is down");
				}));

		assertThat(scrape()).contains("http_requests_total{method=\"POST\",route=\"/orders\",status=\"500\"} 1.0");
	}

	private String scrape() {
		return MetricsText.scrape(registry);
	}

}
