package com.example.edu_shop.exporter;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

/**
 * Превращает JSON из GET /internal/stats в Prometheus-метрики.
 * <p>
 * API опрашивается не по расписанию, а в момент, когда Prometheus запрашивает /metrics у exporter:
 * так значения всегда свежие, а если API недоступно, Prometheus сразу увидит {@code shop_stats_up 0},
 * а не последние сохранённые числа.
 */
public class ShopStatsCollector implements MultiCollector {

	private static final Logger log = LoggerFactory.getLogger(ShopStatsCollector.class);

	private final ApiStatsClient client;

	private final Clock clock;

	public ShopStatsCollector(ApiStatsClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
	}

	@Override
	public MetricSnapshots collect() {
		ApiStats stats;
		try {
			stats = client.fetch();
		} catch (RestClientException ex) {
			log.warn("Failed to get stats from API: {}", ex.getMessage());
			return MetricSnapshots.of(up(false));
		}

		List<MetricSnapshot> snapshots = new ArrayList<>(List.of(
				up(true),
				gauge("shop_stats_orders_new", "Orders waiting for processing (status NEW)", stats.ordersNew()),
				gauge("shop_stats_orders_processing", "Orders being processed (status PROCESSING)",
						stats.ordersProcessing()),
				gauge("shop_stats_orders_done", "Processed orders (status DONE)", stats.ordersDone())));
		// Пока ни один заказ не обработан, считать время не от чего: метрику не отдаём
		if (stats.lastProcessedAt() != null) {
			Duration sinceLastProcessed = Duration.between(stats.lastProcessedAt(), clock.instant());
			snapshots.add(gauge("shop_stats_seconds_since_last_processed",
					"Seconds since the last order became DONE", sinceLastProcessed.toMillis() / 1000.0));
		}
		return new MetricSnapshots(snapshots);
	}

	private static GaugeSnapshot up(boolean success) {
		return gauge("shop_stats_up", "Whether the last request to API /internal/stats succeeded (1) or not (0)",
				success ? 1 : 0);
	}

	private static GaugeSnapshot gauge(String name, String help, double value) {
		return GaugeSnapshot.builder()
				.name(name)
				.help(help)
				.dataPoint(GaugeDataPointSnapshot.builder().value(value).build())
				.build();
	}

}
