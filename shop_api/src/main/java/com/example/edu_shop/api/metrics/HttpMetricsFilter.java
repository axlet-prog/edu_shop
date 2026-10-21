package com.example.edu_shop.api.metrics;

import java.io.IOException;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Считает HTTP-метрики API: {@code http_requests_total} и {@code http_request_duration_seconds}
 * с метками {@code method}, {@code route} и {@code status}.
 * <p>
 * В {@code route} попадает шаблон пути из Spring MVC ({@code /orders/{id}}), а не сам путь:
 * иначе каждый id заказа порождал бы в Prometheus новый временной ряд. Несуществующие пути получают
 * {@code route="/**"}: этот шаблон у обработчика статических ресурсов Spring Boot.
 */
public class HttpMetricsFilter extends OncePerRequestFilter {

	/**
	 * Метка route, если Spring MVC отклонил запрос ещё при поиске обработчика и шаблон пути неизвестен.
	 * Например, путь есть, но метод не тот: {@code POST /health} → 405.
	 */
	static final String UNKNOWN_ROUTE = "UNKNOWN";

	private final Counter requests;

	private final Histogram duration;

	public HttpMetricsFilter(PrometheusRegistry registry) {
		this.requests = Counter.builder()
				.name("http_requests_total")
				.help("HTTP requests handled by API")
				.labelNames("method", "route", "status")
				.register(registry);
		this.duration = Histogram.builder()
				.name("http_request_duration_seconds")
				.help("HTTP request duration in seconds")
				.labelNames("method", "route", "status")
				// от 5 мс до 10 с
				.classicUpperBounds(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
				.classicOnly()
				.register(registry);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		boolean failed = true;
		try {
			chain.doFilter(request, response);
			failed = false;
		} finally {
			// Необработанное исключение Tomcat превратит в ответ 500 уже после выхода из фильтра
			int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
			String[] labels = {request.getMethod(), route(request), String.valueOf(status)};
			requests.labelValues(labels).inc();
			duration.labelValues(labels).observe(Unit.nanosToSeconds(System.nanoTime() - start));
		}
	}

	private static String route(HttpServletRequest request) {
		// Атрибут выставляет Spring MVC, когда находит обработчик запроса
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern != null ? pattern.toString() : UNKNOWN_ROUTE;
	}

}
