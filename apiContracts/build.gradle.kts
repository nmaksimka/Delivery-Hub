plugins {
    id("deliveryhub.java-conventions")
    `java-library`
    alias(libs.plugins.avro)
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.avro)
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    compileOnly(libs.tomcat.annotations)

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

protobuf {
    protoc {
        artifact = "${libs.protobuf.protoc.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "${libs.grpc.protoc.gen.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}
