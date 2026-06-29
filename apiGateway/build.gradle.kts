plugins {
    id("deliveryhub.platform-client")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.springdoc.webflux.ui)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.bundles.jjwt.runtime)

    testImplementation(libs.spring.boot.starter.security.test)
}
