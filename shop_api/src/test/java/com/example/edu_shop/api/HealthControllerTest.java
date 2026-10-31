package com.example.edu_shop.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

	@Test
	void upWhenChaosModeIsOff() {
		// Доля ошибок 1.0 не должна ни на что влиять, пока режим выключен
		ResponseEntity<?> response = new HealthController(false, 1.0).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasToString("{status=UP}");
	}

	@Test
	void failsWhenChaosModeIsOn() {
		ResponseEntity<?> response = new HealthController(true, 1.0).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).hasToString("{status=DOWN}");
	}

	@Test
	void upInChaosModeWithZeroErrorRate() {
		ResponseEntity<?> response = new HealthController(true, 0.0).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}
