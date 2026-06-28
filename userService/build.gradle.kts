plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.bundles.jjwt.runtime)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation(libs.spring.boot.starter.security.test)
}
