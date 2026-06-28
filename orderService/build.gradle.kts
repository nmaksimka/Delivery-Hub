plugins {
    id("deliveryhub.spring-service")
    id("deliveryhub.integration-test")
}

dependencies {
    implementation(project(":apiContracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.cloud.starter.openfeign)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.kafka.avro.serializer)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    integrationTestImplementation(libs.testcontainers.kafka)
}
