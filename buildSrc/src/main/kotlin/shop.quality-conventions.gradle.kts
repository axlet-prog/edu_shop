// Общие для всех модулей проверки качества: линтер, тесты с порогом покрытия и фиксация версий зависимостей
plugins {
	checkstyle
	jacoco
}

// Версии всех зависимостей фиксируются в gradle.lockfile каждого модуля (аналог package-lock.json).
// STRICT: сборка падает, если в lock-файле нет конфигурации или версия не совпадает.
// Обновить lock-файлы после изменения зависимостей: ./gradlew resolveDependencies --write-locks
dependencyLocking {
	lockAllConfigurations()
	lockMode = LockMode.STRICT
}

tasks.register("resolveDependencies") {
	group = "build setup"
	description = "Resolves all dependencies of the project, verifying them against gradle.lockfile."
	notCompatibleWithConfigurationCache("Resolves configurations at execution time")
	doLast {
		configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
	}
}

checkstyle {
	toolVersion = "14.3.0"
	maxWarnings = 0
}

jacoco {
	toolVersion = "0.8.15"
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	testLogging {
		events("failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
	// Каждый набор тестов завершается своим отчётом: test -> jacocoTestReport,
	// integrationTest -> jacocoIntegrationTestReport. Иначе интеграционный прогон строил бы отчёт unit-тестов
	finalizedBy("jacoco${name.replaceFirstChar { it.uppercase() }}Report")
}

tasks.withType<JacocoReport>().configureEach {
	reports {
		xml.required = true
		csv.required = true
	}
}

// Quality Gate: тесты падают, если покрытие строк или ветвлений в модуле ниже 80%
tasks.withType<JacocoCoverageVerification>().configureEach {
	violationRules {
		rule {
			limit {
				counter = "LINE"
				minimum = "0.80".toBigDecimal()
			}
			limit {
				counter = "BRANCH"
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

pluginManager.withPlugin("java") {
	tasks.named("check") {
		dependsOn(tasks.named("jacocoTestCoverageVerification"))
	}
}
