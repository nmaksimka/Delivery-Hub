plugins {
    id("deliveryhub.platform-client")
    id("deliveryhub.integration-test")
    alias(libs.plugins.openapi.generator)
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)

    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.springdoc.webmvc.ui)
}

val generatedApi = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName = "spring"
    inputSpec = layout.projectDirectory.file("src/main/resources/openapi/payment-api.yaml")
    outputDir = generatedApi
    apiPackage = "com.deliveryhub.payment.api.generated"
    modelPackage = "com.deliveryhub.payment.api.generated.model"
    globalProperties = mapOf("apis" to "", "models" to "")
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "useSpringBoot3" to "false",
        "useSpringBoot4" to "true",
        "useJackson3" to "true",
        "useJakartaEe" to "true",
        "useTags" to "true",
        "useBeanValidation" to "true",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "hideGenerationTimestamp" to "true"
    )
}

sourceSets {
    main {
        java.srcDir(generatedApi.map { it.dir("src/main/java") })
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}
