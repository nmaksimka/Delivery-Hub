plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
}

val restaurantStubs = fileTree(project(":restaurantService").layout.buildDirectory.dir("libs")) {
    include("*-stubs.jar")
}.builtBy(":restaurantService:verifierStubsJar")

dependencies {
    implementation(project(":apiContracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.grpc.client.starter)
    implementation(libs.spring.cloud.starter.circuitbreaker.resilience4j)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.kafka.avro.serializer)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    integrationTestImplementation(libs.testcontainers.kafka)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.wiremock)
    testImplementation(libs.spring.cloud.starter.contract.stub.runner)
    testRuntimeOnly(restaurantStubs)
    integrationTestImplementation(libs.grpc.inprocess)
}
