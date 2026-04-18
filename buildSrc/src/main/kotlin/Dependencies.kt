object Dependencies {
    const val springBootStarterWeb = "org.springframework.boot:spring-boot-starter-web"
    const val springBootStarterDataJpa = "org.springframework.boot:spring-boot-starter-data-jpa"
    const val springBootStarterValidation = "org.springframework.boot:spring-boot-starter-validation"
    const val springBootStarterActuator = "org.springframework.boot:spring-boot-starter-actuator"
    const val springBootStarterTest = "org.springframework.boot:spring-boot-starter-test"

    const val springCloudStarterOpenfeign = "org.springframework.cloud:spring-cloud-starter-openfeign"

    const val springKafka = "org.springframework.kafka:spring-kafka"

    const val postgresql = "org.postgresql:postgresql:${Versions.postgresql}"
    const val flywayCore = "org.flywaydb:flyway-core:${Versions.flyway}"
    const val flywayPostgresql = "org.flywaydb:flyway-database-postgresql:${Versions.flyway}"

    const val micrometerPrometheus = "io.micrometer:micrometer-registry-prometheus:${Versions.micrometerPrometheus}"

    const val lombok = "org.projectlombok:lombok:${Versions.lombok}"
    const val mapstruct = "org.mapstruct:mapstruct:${Versions.mapstruct}"
    const val mapstructProcessor = "org.mapstruct:mapstruct-processor:${Versions.mapstruct}"

    const val springdocOpenapi = "org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.springdocOpenapi}"

    const val testcontainersBom = "org.testcontainers:testcontainers-bom:${Versions.testcontainers}"
    const val testcontainersJunit = "org.testcontainers:junit-jupiter"
    const val testcontainersPostgresql = "org.testcontainers:postgresql"
    const val testcontainersKafka = "org.testcontainers:kafka"
    const val springKafkaTest = "org.springframework.kafka:spring-kafka-test"
}