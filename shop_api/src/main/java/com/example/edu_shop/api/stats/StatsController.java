package com.example.edu_shop.api.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Техническая ручка для собственного exporter: обычный JSON, а не Prometheus-метрики.
 */
@RestController
public class StatsController {

	private final OrderStatsRepository statsRepository;

	public StatsController(OrderStatsRepository statsRepository) {
		this.statsRepository = statsRepository;
	}

	@GetMapping("/internal/stats")
	public OrderStats stats() {
		return statsRepository.load();
	}

}
