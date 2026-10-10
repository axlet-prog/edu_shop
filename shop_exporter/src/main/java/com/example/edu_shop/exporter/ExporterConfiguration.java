package com.example.edu_shop.exporter;

import java.time.Clock;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExporterConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ShopStatsCollector shopStatsCollector(ApiStatsClient client, Clock clock, PrometheusRegistry registry) {
		ShopStatsCollector collector = new ShopStatsCollector(client, clock);
		// Ручка /metrics отдаёт всё, что зарегистрировано в этом реестре
		registry.register(collector);
		return collector;
	}

}
