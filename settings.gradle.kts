rootProject.name = "edu_shop"

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

include("shop_common", "shop_api", "shop_worker", "shop_exporter")
