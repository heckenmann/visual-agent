plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ktlint)
    jacoco
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories {
    mavenCentral()
}

dependencies {
    api(platform(libs.grpc.bom))
    api(platform(libs.protobuf.bom))
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    compileOnly(libs.javax.annotation.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.java.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror")
    }
}

ktlint {
    version.set("1.5.0")
    android.set(false)
}
