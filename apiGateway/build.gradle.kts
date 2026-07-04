plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test-base")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.springdoc.webflux.ui)

    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.data.redis.reactive)

    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.wiremock)

    integrationTestImplementation(libs.testcontainers.core)
}
