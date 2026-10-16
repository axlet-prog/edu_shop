package com.example.edu_shop.worker;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * Сколько Worker «обрабатывает» один заказ: для каждого заказа случайное время от {@code processingDelayMin}
 * до {@code processingDelayMax} включительно. Чтобы задержка была постоянной, задайте обе границы одинаковыми.
 *
 * @param processingDelayMin нижняя граница задержки (переменная окружения PROCESSING_DELAY_MIN_MS)
 * @param processingDelayMax верхняя граница задержки (переменная окружения PROCESSING_DELAY_MAX_MS)
 */
@ConfigurationProperties("shop.worker")
public record WorkerProperties(
		@DurationUnit(ChronoUnit.MILLIS) Duration processingDelayMin,
		@DurationUnit(ChronoUnit.MILLIS) Duration processingDelayMax) {

	public WorkerProperties {
		// Ошибку в настройках лучше увидеть при старте Worker-а, а не на первом заказе
		if (processingDelayMin.isNegative() || processingDelayMax.compareTo(processingDelayMin) < 0) {
			throw new IllegalArgumentException("Processing delay must satisfy 0 <= min <= max, got min=%d ms, max=%d ms"
					.formatted(processingDelayMin.toMillis(), processingDelayMax.toMillis()));
		}
	}

	/**
	 * Случайная задержка от {@code processingDelayMin} до {@code processingDelayMax} включительно,
	 * с точностью до миллисекунды.
	 */
	public Duration randomProcessingDelay() {
		long millis = ThreadLocalRandom.current()
				.nextLong(processingDelayMin.toMillis(), processingDelayMax.toMillis() + 1);
		return Duration.ofMillis(millis);
	}

}
