package com.example.edu_shop.api;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проверка работоспособности для проб Kubernetes и healthcheck Docker.
 * <p>
 * {@code CHAOS_MODE=true} включает намеренную поломку: доля {@code CHAOS_ERROR_RATE} ответов (по умолчанию 30%)
 * получает 500. Так собирается заведомо «сломанный» образ, на котором Argo Rollouts демонстрирует
 * автоматический откат по доле ошибок 5xx в {@code http_requests_total}.
 */
@RestController
public class HealthController {

	private final boolean chaosMode;

	private final double errorRate;

	public HealthController(@Value("${CHAOS_MODE:false}") boolean chaosMode,
			@Value("${CHAOS_ERROR_RATE:0.3}") double errorRate) {
		this.chaosMode = chaosMode;
		this.errorRate = errorRate;
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		if (chaosMode && ThreadLocalRandom.current().nextDouble() < errorRate) {
			return ResponseEntity.internalServerError().body(Map.of("status", "DOWN"));
		}
		return ResponseEntity.ok(Map.of("status", "UP"));
	}

}
