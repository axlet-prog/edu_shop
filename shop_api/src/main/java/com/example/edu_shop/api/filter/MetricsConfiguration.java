package com.example.edu_shop.api.filter;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

	@Bean
	FilterRegistrationBean<HttpMetricsFilter> httpMetricsFilter(PrometheusRegistry registry) {
		FilterRegistrationBean<HttpMetricsFilter> registration =
				new FilterRegistrationBean<>(new HttpMetricsFilter(registry));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
		return registration;
	}

	@Bean
	FilterRegistrationBean<HeaderRequestFilter> headerRequestFilterFilter() {
		FilterRegistrationBean<HeaderRequestFilter> registration =
				new FilterRegistrationBean<>(new HeaderRequestFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
