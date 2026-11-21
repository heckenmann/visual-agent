import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

// Build logic runs on JDK 27 with a bytecode target supported by Gradle's embedded Kotlin.
val buildLogicJvmTarget = JavaVersion.VERSION_24

java {
    sourceCompatibility = buildLogicJvmTarget
    targetCompatibility = buildLogicJvmTarget
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(buildLogicJvmTarget.toString()))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Render icons on the JVM without adding Batik to the runtime application.
    implementation("org.apache.xmlgraphics:batik-transcoder:1.20")
    implementation("org.apache.xmlgraphics:batik-codec:1.20")
}
