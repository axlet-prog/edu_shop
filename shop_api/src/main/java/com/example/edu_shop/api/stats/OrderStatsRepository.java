package com.example.edu_shop.api.stats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderStatsRepository {

	private final JdbcClient jdbcClient;

	public OrderStatsRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public OrderStats load() {
		return jdbcClient.sql("""
				SELECT count(*) FILTER (WHERE status = 'NEW')        AS orders_new,
				       count(*) FILTER (WHERE status = 'PROCESSING') AS orders_processing,
				       count(*) FILTER (WHERE status = 'DONE')       AS orders_done,
				       max(processed_at)                             AS last_processed_at
				FROM orders
				""")
				.query(OrderStats.class)
				.single();
	}

}
