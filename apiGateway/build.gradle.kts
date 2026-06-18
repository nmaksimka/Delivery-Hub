plugins {
    id("deliveryhub.spring-service")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.springdoc.webflux.ui)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.bundles.jjwt.runtime)

    testImplementation(libs.spring.security.test)
}
