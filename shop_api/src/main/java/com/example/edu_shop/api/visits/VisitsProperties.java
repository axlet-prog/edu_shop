package com.example.edu_shop.api.visits;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cacheTtl сколько счётчик посещений живёт в Redis (переменная окружения VISITS_CACHE_TTL)
 */
@ConfigurationProperties("shop.visits")
public record VisitsProperties(Duration cacheTtl) {

	public VisitsProperties {
		// Redis не принимает нулевой или отрицательный TTL, ошибку лучше увидеть при старте API
		if (cacheTtl.isZero() || cacheTtl.isNegative()) {
			throw new IllegalArgumentException("Visits cache TTL must be positive, got " + cacheTtl);
		}
	}

}
