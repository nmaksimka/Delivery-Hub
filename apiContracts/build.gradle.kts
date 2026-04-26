plugins {
    java
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

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.0")
    compileOnly(Dependencies.lombok)
    annotationProcessor(Dependencies.lombok)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
