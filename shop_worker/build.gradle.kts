plugins {
	java
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
}
