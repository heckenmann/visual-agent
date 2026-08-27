plugins {
    `java-library`
}

group = "de.heckenmann.visualagent"
version = libs.versions.visual.agent.get()

repositories {
    mavenCentral()
}

dependencies {
    api(project(":provider-standard"))
    api(project(":provider-openai-codex"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
