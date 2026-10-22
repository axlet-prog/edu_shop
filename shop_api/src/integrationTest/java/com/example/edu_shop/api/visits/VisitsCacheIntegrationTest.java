package com.example.edu_shop.api.visits;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VisitsCacheIntegrationTest extends VisitsIntegrationTestSupport {

	@Test
	void secondRequestWithinTtlIsServedFromCache() {
		// Тест сам прогревает кэш первым запросом и не полагается на то, что это сделал другой тест
		client.get().uri("/visits").exchange().expectStatus().isOk();

		client.get().uri("/visits")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.source").isEqualTo("cache")
				.jsonPath("$.count").isEqualTo(1);
	}

	@Test
	void cachedCountExpiresAfterConfiguredTtl() {
		client.get().uri("/visits").exchange().expectStatus().isOk();

		// TTL ставит сам Redis (SET ... EX), по умолчанию 10 секунд: shop.visits.cache-ttl в application.yaml
		Long ttlSeconds = app.getBean(StringRedisTemplate.class).getExpire(VisitService.CACHE_KEY, TimeUnit.SECONDS);
		assertThat(ttlSeconds).isBetween(1L, 10L);
	}

}
