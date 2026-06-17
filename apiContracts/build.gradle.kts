plugins {
    id("deliveryhub.java-conventions")
}

dependencies {
    implementation(libs.jackson.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
