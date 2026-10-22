package com.example.edu_shop.api.visits;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VisitsPropertiesTest {

	@Test
	void keepsCacheTtl() {
		VisitsProperties properties = new VisitsProperties(Duration.ofSeconds(10));

		assertThat(properties.cacheTtl()).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void rejectsZeroTtl() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new VisitsProperties(Duration.ZERO))
				.withMessageContaining("PT0S");
	}

	@Test
	void rejectsNegativeTtl() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new VisitsProperties(Duration.ofSeconds(-1)))
				.withMessageContaining("PT-1S");
	}

}
