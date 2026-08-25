plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
}

dependencies {
    implementation(project(":apiContracts"))
    implementation(libs.spring.grpc.server.starter)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.ratelimiter)
    implementation(libs.resilience4j.micrometer)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.aws.sdk.s3)
    implementation(libs.springdoc.webmvc.ui)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    integrationTestImplementation(libs.testcontainers.kafka)
    testImplementation(libs.wiremock)
    testImplementation(libs.grpc.inprocess)
}
