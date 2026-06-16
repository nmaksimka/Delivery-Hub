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

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${Versions.springCloud}")
    }
}

dependencies {
    implementation(Dependencies.springCloudStarterGateway)
    implementation(Dependencies.springBootStarterActuator)
    implementation(Dependencies.springBootStarterSecurity)
    runtimeOnly(Dependencies.micrometerPrometheus)

    implementation(Dependencies.springdocOpenapiWebflux)

    implementation(Dependencies.jjwtApi)
    runtimeOnly(Dependencies.jjwtImpl)
    runtimeOnly(Dependencies.jjwtJackson)

    compileOnly(Dependencies.lombok)
    annotationProcessor(Dependencies.lombok)

    testImplementation(Dependencies.springBootStarterTest)
    testImplementation(Dependencies.mockitoJunitJupiter)
    testImplementation(Dependencies.springSecurityTest)
    testRuntimeOnly(Dependencies.jjwtImpl)
    testRuntimeOnly(Dependencies.jjwtJackson)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
