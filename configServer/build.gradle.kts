plugins {
    id("deliveryhub.spring-service")
}

dependencies {
    implementation(libs.spring.cloud.config.server)
}

tasks.bootRun {
    workingDir = rootDir
}
