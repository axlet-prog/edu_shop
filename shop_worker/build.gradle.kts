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
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
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
