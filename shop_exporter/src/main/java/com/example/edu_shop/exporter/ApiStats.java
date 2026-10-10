package com.example.edu_shop.exporter;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ API на GET /internal/stats. Новые поля в ответе не ломают exporter, они просто игнорируются.
 *
 * @param lastProcessedAt когда последний заказ перешёл в DONE; {@code null}, пока ни один заказ не обработан
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiStats(
		@JsonProperty("orders_new") long ordersNew,
		@JsonProperty("orders_processing") long ordersProcessing,
		@JsonProperty("orders_done") long ordersDone,
		@JsonProperty("last_processed_at") Instant lastProcessedAt) {
}
