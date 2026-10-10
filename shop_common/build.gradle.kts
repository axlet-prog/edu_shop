import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
	`java-library`
	id("shop.quality-conventions")
	id("io.spring.dependency-management")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencyManagement {
	imports {
		mavenBom(SpringBootPlugin.BOM_COORDINATES)
	}
}

dependencies {
	api("org.springframework.boot:spring-boot-starter-data-jdbc")
	api("org.springframework.boot:spring-boot-starter-amqp")
	api("org.springframework.boot:spring-boot-starter-jackson")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Spring Boot plugin добавляет этот флаг сам, но к библиотеке он не применяется.
// Без него не работают именованные параметры (:id) в @Query.
tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}
