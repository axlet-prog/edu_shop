package com.example.edu_shop.api.visits;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class VisitsDatabaseIntegrationTest extends VisitsIntegrationTestSupport {

	@Test
	void firstRequestCreatesRecordInDatabase() {
		client.get().uri("/visits")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				// База новая для каждого теста, поэтому значения известны точно, а не просто «больше нуля»
				.jsonPath("$.source").isEqualTo("db")
				.jsonPath("$.count").isEqualTo(1)
				.jsonPath("$.last_id").isEqualTo(1);

		long rows = app.getBean(JdbcClient.class).sql("SELECT count(*) FROM visits").query(Long.class).single();
		assertThat(rows).isEqualTo(1);
	}

}
