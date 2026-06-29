plugins {
    id("deliveryhub.spring-service")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("spring-cloud-starter-eureka-client").get())
    "implementation"(libs.findLibrary("spring-cloud-starter-config").get())
}

tasks.withType<Test>().configureEach {
    systemProperty("eureka.client.enabled", "false")
    systemProperty("spring.cloud.config.enabled", "false")
}
