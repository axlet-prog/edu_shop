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
	// Кэш счётчика посещений (GET /visits)
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// Actuator отдаёт /metrics. Свои метрики пишем клиентом Prometheus, Micrometer добавляет метрики JVM
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.prometheus:prometheus-metrics-core")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("io.prometheus:prometheus-metrics-exposition-textformats")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit-тесты лежат в src/test и запускаются `./gradlew test` без Docker.
// Интеграционные тесты лежат в src/integrationTest и запускаются `./gradlew integrationTest`:
// они поднимают PostgreSQL, Redis и RabbitMQ через Testcontainers, поэтому нужен Docker.
// Testcontainers подключён только к интеграционным тестам, в unit-тестах его нет даже на classpath.
testing {
	suites {
		register<JvmTestSuite>("integrationTest") {
			// Версия JUnit та же, что у unit-тестов: из BOM Spring Boot, а не версия Gradle по умолчанию
			useJUnitJupiter(dependencyManagement.importedProperties.getValue("junit-jupiter.version"))
			// Классы приложения, как у src/test. Через project() они бы не подключились: обычный jar отключён
			sources {
				compileClasspath += sourceSets.main.get().output
				runtimeClasspath += sourceSets.main.get().output
			}
			dependencies {
				implementation("org.springframework.boot:spring-boot-testcontainers")
				implementation("org.testcontainers:testcontainers-junit-jupiter")
				implementation("org.testcontainers:testcontainers-postgresql")
				implementation("org.testcontainers:testcontainers-rabbitmq")
			}
			targets.configureEach {
				testTask.configure {
					// Параллельность средствами JUnit: тестовые классы выполняются одновременно в потоках одной JVM
					// (число потоков = числу ядер), тесты внутри класса последовательно, как файлы у Jest.
					// Пока один класс ждёт запуска своих контейнеров, другие уже работают.
					// Отдельные JVM (maxParallelForks) проверены и не ускоряют: каждая заново прогревается.
					// Без параллельности (аналог --runInBand): ./gradlew integrationTest -PintegrationTest.parallel=false
					val parallel = providers.gradleProperty("integrationTest.parallel").orNull?.toBoolean() ?: true
					systemProperty("junit.jupiter.execution.parallel.enabled", parallel)
					systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
					systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
					doFirst { logger.lifecycle("Integration tests: parallel execution of test classes = $parallel") }
					testLogging {
						events("passed", "skipped", "failed")
					}
					shouldRunAfter(tasks.test)
				}
			}
		}
	}
}

// Интеграционным тестам доступно всё, что и unit-тестам (Spring Boot Test, MockMvc, AssertJ)
configurations.named("integrationTestImplementation") {
	extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
	extendsFrom(configurations.testRuntimeOnly.get())
}

// Порог покрытия разделён между уровнями тестов. Код из этого списка (SQL-запросы, Redis, сборка
// Spring-контекста и ручки без собственной логики) целенаправленно проверяется интеграционными тестами,
// поэтому unit-порог его не учитывает, а интеграционный порог (те же 80%) считается только по нему.
val integrationTestedClasses = listOf(
	"com/example/edu_shop/ApiApplication*",
	"com/example/edu_shop/api/HealthController*",
	"com/example/edu_shop/api/metrics/MetricsConfiguration*",
	"com/example/edu_shop/api/stats/**",
	"com/example/edu_shop/api/visits/VisitController*",
	"com/example/edu_shop/api/visits/VisitService*",
	"com/example/edu_shop/api/visits/VisitsResponse*",
)
val mainClasses = sourceSets.main.map { it.output.classesDirs.asFileTree }
val unitScope = mainClasses.map { it.matching { exclude(integrationTestedClasses) } }
val integrationScope = mainClasses.map { it.matching { include(integrationTestedClasses) } }

tasks.jacocoTestReport {
	classDirectories.setFrom(unitScope)
}
tasks.jacocoTestCoverageVerification {
	classDirectories.setFrom(unitScope)
}

val integrationTest = tasks.named<Test>("integrationTest")

// Имя важно: convention-плагин завершает каждый набор тестов отчётом jacoco<Набор>Report
val jacocoIntegrationTestReport by tasks.registering(JacocoReport::class) {
	group = "verification"
	description = "Coverage report of the integration tests (only classes they are responsible for)."
	executionData(integrationTest.get())
	sourceSets(sourceSets.main.get())
	classDirectories.setFrom(integrationScope)
	reports {
		html.outputLocation = layout.buildDirectory.dir("reports/jacoco/integrationTest/html")
		xml.outputLocation = layout.buildDirectory.file("reports/jacoco/integrationTest/jacocoIntegrationTestReport.xml")
		csv.outputLocation = layout.buildDirectory.file("reports/jacoco/integrationTest/jacocoIntegrationTestReport.csv")
	}
}

val jacocoIntegrationTestCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
	group = "verification"
	description = "Integration tests coverage gate (only classes they are responsible for)."
	executionData(integrationTest.get())
	sourceSets(sourceSets.main.get())
	classDirectories.setFrom(integrationScope)
}

// `./gradlew build` по-прежнему проверяет всё: unit-тесты, интеграционные тесты и оба порога покрытия
tasks.check {
	dependsOn(integrationTest, jacocoIntegrationTestCoverageVerification)
}

// Имя без версии, чтобы скрипты запуска и Dockerfile не зависели от версии проекта
tasks.bootJar {
	archiveFileName = "shop_api.jar"
}

// Обычный jar без зависимостей (-plain.jar) не нужен: от приложения не зависит ни один модуль
tasks.jar {
	enabled = false
}
