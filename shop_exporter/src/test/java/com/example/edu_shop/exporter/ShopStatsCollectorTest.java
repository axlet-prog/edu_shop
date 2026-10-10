package com.example.edu_shop.exporter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ShopStatsCollectorTest {

	private static final Instant NOW = Instant.parse("2026-09-04T18:30:40Z");

	private final ApiStatsClient client = mock(ApiStatsClient.class);

	private final ShopStatsCollector collector = new ShopStatsCollector(client, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void convertsStatsToGauges() {
		given(client.fetch()).willReturn(new ApiStats(2, 4, 120, Instant.parse("2026-09-04T18:30:10Z")));

		assertThat(gauges(collector.collect())).containsExactlyInAnyOrderEntriesOf(Map.of(
				"shop_stats_up", 1.0,
				"shop_stats_orders_new", 2.0,
				"shop_stats_orders_processing", 4.0,
				"shop_stats_orders_done", 120.0,
				"shop_stats_seconds_since_last_processed", 30.0));
	}

	@Test
	void skipsSecondsSinceLastProcessedUntilFirstOrderIsDone() {
		given(client.fetch()).willReturn(new ApiStats(1, 0, 0, null));

		assertThat(gauges(collector.collect()))
				.contains(entry("shop_stats_up", 1.0), entry("shop_stats_orders_new", 1.0))
				.doesNotContainKey("shop_stats_seconds_since_last_processed");
	}

	@Test
	void reportsOnlyUpZeroWhenApiIsUnavailable() {
		given(client.fetch()).willThrow(new ResourceAccessException("Connection refused"));

		assertThat(gauges(collector.collect())).containsExactly(entry("shop_stats_up", 0.0));
	}

	private static Map<String, Double> gauges(MetricSnapshots snapshots) {
		return snapshots.stream()
				.map(GaugeSnapshot.class::cast)
				.collect(Collectors.toMap(gauge -> gauge.getMetadata().getName(),
						gauge -> gauge.getDataPoints().getFirst().getValue()));
	}

}
