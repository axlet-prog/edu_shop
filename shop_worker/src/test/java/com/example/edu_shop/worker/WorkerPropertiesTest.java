package com.example.edu_shop.worker;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorkerPropertiesTest {

	@Test
	void keepsProcessingDelay() {
		WorkerProperties properties = new WorkerProperties(Duration.ofMillis(2000));

		assertThat(properties.processingDelay()).isEqualTo(Duration.ofMillis(2000));
	}

	@Test
	void rejectsNegativeDelay() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new WorkerProperties(Duration.ofMillis(-1)))
				.withMessageContaining("-1 ms");
	}

}
