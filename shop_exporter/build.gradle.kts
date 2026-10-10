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

// Exporter не зависит от shop_common: он ничего не знает о базе и очереди и видит API только через HTTP
dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// Actuator отдаёт /metrics. Метрики из /internal/stats собираются через модель клиента Prometheus
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.prometheus:prometheus-metrics-model")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Имя без версии, чтобы скрипты запуска и Dockerfile не зависели от версии проекта
tasks.bootJar {
	archiveFileName = "shop_exporter.jar"
}

// Обычный jar без зависимостей (-plain.jar) не нужен: от приложения не зависит ни один модуль
tasks.jar {
	enabled = false
}
