package com.example.edu_shop.exporter;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiStatsClientTest {

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final ApiStatsClient client =
			new ApiStatsClient(builder, new ExporterProperties(URI.create("http://api:8080")));

	@Test
	void readsStatsFromApi() {
		server.expect(requestTo("http://api:8080/internal/stats")).andRespond(withSuccess("""
				{
				  "orders_new": 2,
				  "orders_processing": 4,
				  "orders_done": 120,
				  "last_processed_at": "2026-09-04T18:30:10Z",
				  "field_added_later": true
				}
				""", MediaType.APPLICATION_JSON));

		assertThat(client.fetch()).isEqualTo(new ApiStats(2, 4, 120, Instant.parse("2026-09-04T18:30:10Z")));
	}

	@Test
	void readsStatsWithoutProcessedOrders() {
		server.expect(requestTo("http://api:8080/internal/stats")).andRespond(withSuccess("""
				{"orders_new": 1, "orders_processing": 0, "orders_done": 0, "last_processed_at": null}
				""", MediaType.APPLICATION_JSON));

		assertThat(client.fetch()).isEqualTo(new ApiStats(1, 0, 0, null));
	}

	@Test
	void failsOnApiError() {
		server.expect(requestTo("http://api:8080/internal/stats")).andRespond(withServerError());

		assertThatExceptionOfType(RestClientException.class).isThrownBy(client::fetch);
	}

	@Test
	void failsOnEmptyResponse() {
		server.expect(requestTo("http://api:8080/internal/stats")).andRespond(withSuccess());

		assertThatExceptionOfType(RestClientException.class).isThrownBy(client::fetch)
				.withMessageContaining("empty");
	}

}
