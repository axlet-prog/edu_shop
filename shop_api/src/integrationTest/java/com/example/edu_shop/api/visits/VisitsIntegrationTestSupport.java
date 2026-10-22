package com.example.edu_shop.api.visits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.edu_shop.ApiApplication;

/**
 * Общая часть интеграционных тестов {@code GET /visits} (аналог {@code tests/integration/helpers.js} из задания).
 *
 * <p>Контейнеры объявлены полями экземпляра, а не static: Testcontainers поднимает новые PostgreSQL и Redis
 * перед каждым тестом и останавливает их после теста, даже если тест упал. Поэтому тесты не зависят
 * от порядка выполнения и могут идти параллельно. Порты контейнеров Docker выдаёт случайные,
 * приложение берёт их у Testcontainers, так что параллельные прогоны не конфликтуют.
 *
 * <p>Порядок для каждого теста: контейнеры → приложение → тест → закрытие приложения (его соединений)
 * → остановка контейнеров.
 */
@Testcontainers(parallel = true)
abstract class VisitsIntegrationTestSupport {

	@Container
	private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Container
	private final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	/**
	 * Настоящее приложение API: миграции Liquibase, настройки из application.yaml, HTTP-сервер на свободном порту.
	 */
	protected ConfigurableApplicationContext app;

	protected RestTestClient client;

	@BeforeEach
	void startApp() {
		app = new SpringApplicationBuilder(ApiApplication.class).run(
				"--server.port=0",
				"--spring.main.banner-mode=off",
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword(),
				"--spring.data.redis.host=" + redis.getHost(),
				"--spring.data.redis.port=" + redis.getMappedPort(6379));
		String port = app.getEnvironment().getRequiredProperty("local.server.port");
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@AfterEach
	void stopApp() {
		// Сначала приложение закрывает соединения с базой и Redis, потом Testcontainers останавливает контейнеры
		app.close();
	}

}
