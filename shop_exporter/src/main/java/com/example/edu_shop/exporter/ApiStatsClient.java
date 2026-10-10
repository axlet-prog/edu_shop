package com.example.edu_shop.exporter;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Клиент технической ручки API GET /internal/stats. Таймауты задаются в spring.http.clients.
 */
@Component
public class ApiStatsClient {

	private final RestClient restClient;

	public ApiStatsClient(RestClient.Builder builder, ExporterProperties properties) {
		this.restClient = builder.baseUrl(properties.apiUrl().toString()).build();
	}

	/**
	 * @throws RestClientException если API недоступно, ответило ошибкой или вернуло пустой ответ
	 */
	public ApiStats fetch() {
		ApiStats stats = restClient.get().uri("/internal/stats").retrieve().body(ApiStats.class);
		if (stats == null) {
			throw new RestClientException("API returned an empty /internal/stats response");
		}
		return stats;
	}

}
