plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")

val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "integrationTestImplementation"(libs.findLibrary("spring-boot-testcontainers").get())
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against real infrastructure in Testcontainers."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}
