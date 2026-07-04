plugins {
    id("deliveryhub.integration-test-base")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "integrationTestImplementation"(libs.findLibrary("spring-boot-starter-data-jpa-test").get())
    "integrationTestImplementation"(libs.findLibrary("spring-boot-starter-webmvc-test").get())
    "integrationTestImplementation"(libs.findLibrary("testcontainers-postgresql").get())
}
