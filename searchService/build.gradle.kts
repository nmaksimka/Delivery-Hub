plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
}

dependencies {
    implementation(project(":apiContracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.spring.boot.starter.elasticsearch)

    implementation(libs.springdoc.webmvc.ui)

    integrationTestImplementation(libs.testcontainers.kafka)
    integrationTestImplementation(libs.testcontainers.elasticsearch)
}
