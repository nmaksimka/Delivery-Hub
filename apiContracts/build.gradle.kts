plugins {
    id("deliveryhub.java-conventions")
    `java-library`
    alias(libs.plugins.avro)
}

dependencies {
    api(libs.avro)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

avro {
    stringType = "String"
    fieldVisibility = "PRIVATE"
    isCreateSetters = false
    isEnableDecimalLogicalType = true
}
