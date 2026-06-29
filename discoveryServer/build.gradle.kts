plugins {
    id("deliveryhub.spring-service")
}

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.cloud.starter.eureka.server)
}
