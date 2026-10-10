package com.example.edu_shop.api.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Фильтр регистрируется здесь, а не через {@code @Component}: иначе его подхватили бы срезы {@code @WebMvcTest},
 * в которых нет {@link PrometheusRegistry}.
 */
@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

	@Bean
	FilterRegistrationBean<HttpMetricsFilter> httpMetricsFilter(PrometheusRegistry registry) {
		FilterRegistrationBean<HttpMetricsFilter> registration =
				new FilterRegistrationBean<>(new HttpMetricsFilter(registry));
		// Первым в цепочке, чтобы в длительность запроса попала работа остальных фильтров
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
