plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
}

dependencies {
    implementation(project(":apiContracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.thymeleaf)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)

    integrationTestImplementation(libs.testcontainers.kafka)
    integrationTestImplementation(libs.wiremock)
}
