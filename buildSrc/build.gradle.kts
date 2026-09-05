plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// Без явного тулчейна Kotlin берёт JVM Gradle-демона (25) и падает в предупреждение
// "Kotlin does not yet support 25 JDK target". Фиксируем ту же версию, что и у сервисов.
kotlin {
    jvmToolchain(21)
}
