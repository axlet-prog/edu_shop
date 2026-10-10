plugins {
	java
	id("shop.quality-conventions")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	implementation(project(":shop_common"))
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	// Actuator отдаёт /metrics. Свои метрики пишем клиентом Prometheus, Micrometer добавляет метрики JVM
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.prometheus:prometheus-metrics-core")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("io.prometheus:prometheus-metrics-exposition-textformats")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-rabbitmq")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Имя без версии, чтобы скрипты запуска и Dockerfile не зависели от версии проекта
tasks.bootJar {
	archiveFileName = "shop_api.jar"
}

// Обычный jar без зависимостей (-plain.jar) не нужен: от приложения не зависит ни один модуль
tasks.jar {
	enabled = false
}
