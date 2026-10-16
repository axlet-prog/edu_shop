package com.example.edu_shop.worker;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorkerPropertiesTest {

	@Test
	void randomProcessingDelayStaysWithinBounds() {
		Duration min = Duration.ofMillis(100);
		Duration max = Duration.ofMillis(110);
		WorkerProperties properties = new WorkerProperties(min, max);

		Set<Duration> delays = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			delays.add(properties.randomProcessingDelay());
		}

		assertThat(delays).allSatisfy(delay -> assertThat(delay).isBetween(min, max));
		// 1000 попыток на 11 возможных значений: обе границы диапазона выпадают практически наверняка
		assertThat(delays).contains(min, max);
	}

	@Test
	void equalBoundsGiveFixedDelay() {
		WorkerProperties properties = new WorkerProperties(Duration.ofMillis(2000), Duration.ofMillis(2000));

		assertThat(properties.randomProcessingDelay()).isEqualTo(Duration.ofMillis(2000));
	}

	@Test
	void rejectsNegativeMin() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new WorkerProperties(Duration.ofMillis(-1), Duration.ofMillis(100)))
				.withMessageContaining("min=-1 ms");
	}

	@Test
	void rejectsMaxBelowMin() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new WorkerProperties(Duration.ofMillis(3000), Duration.ofMillis(1000)))
				.withMessageContaining("min=3000 ms, max=1000 ms");
	}

}
