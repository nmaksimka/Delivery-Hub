plugins {
    id("deliveryhub.spring-service")
}

dependencies {
    implementation(project(":apiContracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.cloud.starter.openfeign)
    implementation(libs.spring.boot.starter.kafka)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
