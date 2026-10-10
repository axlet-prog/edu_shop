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
	// Веб-сервер нужен только для технической ручки /metrics, пользовательских HTTP-ручек у Worker нет
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// Actuator отдаёт /metrics. Свои метрики пишем клиентом Prometheus, Micrometer добавляет метрики JVM
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.prometheus:prometheus-metrics-core")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.prometheus:prometheus-metrics-exposition-textformats")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Имя без версии, чтобы скрипты запуска и Dockerfile не зависели от версии проекта
tasks.bootJar {
	archiveFileName = "shop_worker.jar"
}

// Обычный jar без зависимостей (-plain.jar) не нужен: от приложения не зависит ни один модуль
tasks.jar {
	enabled = false
}
