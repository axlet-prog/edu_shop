package com.example.edu_shop.exporter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Весь путь целиком: Prometheus запрашивает /metrics, exporter вызывает /internal/stats у API
 * (здесь его заменяет MockRestServiceServer) и отдаёт поля JSON как метрики.
 */
@SpringBootTest(properties = "shop.exporter.api-url=http://api:8080")
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
class ExporterApplicationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MockRestServiceServer api;

	@Test
	void metricsEndpointExposesStatsFromApi() throws Exception {
		api.expect(requestTo("http://api:8080/internal/stats")).andRespond(withSuccess("""
				{"orders_new": 2, "orders_processing": 4, "orders_done": 120, "last_processed_at": null}
				""", MediaType.APPLICATION_JSON));

		mvc.perform(get("/metrics"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("# TYPE shop_stats_orders_new gauge")))
				.andExpect(content().string(containsString("shop_stats_orders_new 2.0")))
				.andExpect(content().string(containsString("shop_stats_orders_processing 4.0")))
				.andExpect(content().string(containsString("shop_stats_orders_done 120.0")))
				.andExpect(content().string(containsString("shop_stats_up 1.0")));
		api.verify();
	}

}
