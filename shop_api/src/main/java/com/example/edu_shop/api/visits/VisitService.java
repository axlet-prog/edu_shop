package com.example.edu_shop.api.visits;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Счётчик посещений: запись в PostgreSQL, результат кэшируется в Redis.
 * Пока счётчик лежит в кэше, запросы отвечают из Redis и в базу не ходят.
 */
@Service
public class VisitService {

	static final String CACHE_KEY = "visits_count";

	private final JdbcClient jdbcClient;

	private final StringRedisTemplate redis;

	private final VisitsProperties properties;

	public VisitService(JdbcClient jdbcClient, StringRedisTemplate redis, VisitsProperties properties) {
		this.jdbcClient = jdbcClient;
		this.redis = redis;
		this.properties = properties;
	}

	public VisitsResponse registerVisit() {
		String cached = redis.opsForValue().get(CACHE_KEY);
		if (cached != null) {
			return VisitsResponse.fromCache(Long.parseLong(cached));
		}

		long lastId = jdbcClient.sql("INSERT INTO visits (created_at) VALUES (now()) RETURNING id")
				.query(Long.class)
				.single();
		long count = jdbcClient.sql("SELECT count(*) FROM visits")
				.query(Long.class)
				.single();

		redis.opsForValue().set(CACHE_KEY, Long.toString(count), properties.cacheTtl());
		return VisitsResponse.fromDatabase(count, lastId);
	}

}
