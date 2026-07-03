plugins {
    id("deliveryhub.platform-client")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.springdoc.webflux.ui)

    implementation(libs.spring.boot.starter.oauth2.resource.server)

    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.wiremock)
}
