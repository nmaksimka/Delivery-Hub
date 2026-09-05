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
    implementation(Dependencies.springBootStarterSecurity)

    implementation(Dependencies.jjwtApi)
    runtimeOnly(Dependencies.jjwtImpl)
    runtimeOnly(Dependencies.jjwtJackson)

    runtimeOnly(Dependencies.postgresql)
    implementation(Dependencies.flywayCore)
    runtimeOnly(Dependencies.flywayPostgresql)

    implementation(Dependencies.springdocOpenapi)
    runtimeOnly(Dependencies.micrometerPrometheus)

    compileOnly(Dependencies.lombok)
    annotationProcessor(Dependencies.lombok)

    implementation(Dependencies.mapstruct)
    annotationProcessor(Dependencies.mapstructProcessor)
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation(Dependencies.springBootStarterTest)
    testImplementation(Dependencies.mockitoJunitJupiter)
    testImplementation(Dependencies.springSecurityTest)
    testRuntimeOnly(Dependencies.jjwtImpl)
    testRuntimeOnly(Dependencies.jjwtJackson)
    testImplementation(platform(Dependencies.testcontainersBom))
    testImplementation(Dependencies.testcontainersJunit)
    testImplementation(Dependencies.testcontainersPostgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
