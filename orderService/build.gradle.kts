plugins {
    java
    id("org.springframework.boot") version Versions.springBoot
    id("io.spring.dependency-management") version Versions.springDependencyManagement
}

group = "com.deliveryhub"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${Versions.springCloud}")
    }
}

dependencies {
    implementation(Dependencies.springBootStarterWeb)
    implementation(Dependencies.springBootStarterDataJpa)
    implementation(Dependencies.springBootStarterValidation)
    implementation(Dependencies.springBootStarterActuator)

    implementation(Dependencies.springCloudStarterOpenfeign)

    implementation(Dependencies.springKafka)

    implementation(Dependencies.postgresql)
    implementation(Dependencies.flywayCore)
    implementation(Dependencies.flywayPostgresql)

    implementation(Dependencies.springdocOpenapi)
    implementation(Dependencies.micrometerPrometheus)

    compileOnly(Dependencies.lombok)
    annotationProcessor(Dependencies.lombok)

    implementation(Dependencies.mapstruct)
    annotationProcessor(Dependencies.mapstructProcessor)

    implementation(project(":apiContracts"))

    testImplementation(Dependencies.springBootStarterTest)
    testImplementation(platform(Dependencies.testcontainersBom))
    testImplementation(Dependencies.testcontainersJunit)
    testImplementation(Dependencies.testcontainersPostgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}