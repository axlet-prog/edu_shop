package com.example.edu_shop.worker;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * @param processingDelay сколько Worker «обрабатывает» один заказ (переменная окружения PROCESSING_DELAY_MS)
 */
@ConfigurationProperties("shop.worker")
public record WorkerProperties(@DurationUnit(ChronoUnit.MILLIS) Duration processingDelay) {
}
