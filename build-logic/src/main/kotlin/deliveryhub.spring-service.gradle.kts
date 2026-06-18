plugins {
    id("deliveryhub.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.findVersion("spring-cloud").get()}")
    }
}

dependencies {
    "implementation"(libs.findLibrary("spring-boot-starter-actuator").get())
    "runtimeOnly"(libs.findLibrary("micrometer-prometheus").get())

    "compileOnly"(libs.findLibrary("lombok").get())
    "annotationProcessor"(libs.findLibrary("lombok").get())

    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testImplementation"(libs.findLibrary("mockito-junit-jupiter").get())
}
