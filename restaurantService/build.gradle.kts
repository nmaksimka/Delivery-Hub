plugins {
    java
    id("org.springframework.boot") version Versions.springBoot
    id("io.spring.dependency-management") version Versions.springDependencyManagement
}

group = "com.deliveryhub"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(Dependencies.springBootStarterWeb)
    implementation(Dependencies.springBootStarterDataJpa)
    implementation(Dependencies.springBootStarterValidation)
    implementation(Dependencies.springBootStarterActuator)

    implementation(Dependencies.postgresql)
    implementation(Dependencies.flywayCore)
    implementation(Dependencies.flywayPostgresql)

    implementation(Dependencies.springdocOpenapi)
    implementation(Dependencies.micrometerPrometheus)

    compileOnly(Dependencies.lombok)
    annotationProcessor(Dependencies.lombok)

    implementation(Dependencies.mapstruct)
    annotationProcessor(Dependencies.mapstructProcessor)

    // Модуль с общими контрактами
    implementation(project(":apiContracts"))

    // Тесты
    testImplementation(Dependencies.springBootStarterTest)
    testImplementation(platform(Dependencies.testcontainersBom))
    testImplementation(Dependencies.testcontainersJunit)
    testImplementation(Dependencies.testcontainersPostgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}