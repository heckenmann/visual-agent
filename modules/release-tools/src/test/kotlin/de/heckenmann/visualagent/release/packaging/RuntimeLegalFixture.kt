package de.heckenmann.visualagent.release.packaging

import java.nio.file.Files
import java.nio.file.Path

/** Minimal source JDK and separately transformed packaged runtime for the real Gradle tasks. */
internal class RuntimeLegalFixture(
    parent: Path,
) {
    val root: Path = Files.createTempDirectory(parent, "runtime-")
    val jdk: Path = root.resolve("fixture-jdk")
    val output: Path = root.resolve("build/materials/common/legal/java-runtime")

    init {
        write(
            "fixture-jdk/release",
            """
            JAVA_VERSION="27.0.1"
            JAVA_RUNTIME_VERSION="27.0.1+9"
            MODULES="java.base java.compiler java.logging java.sql"
            IMPLEMENTOR="Synthetic Vendor"
            OS_NAME="FixtureOS"
            OS_ARCH="x86_64"
            PRIVATE_HOST_PATH="/private/not-for-distribution"
            """.trimIndent() + "\n",
        )
        listOf("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION").forEach {
            write("fixture-jdk/legal/java.base/$it", "Synthetic $it notice\n")
        }
        listOf("lib/modules", "bin/keytool", "bin/jlink", "bin/jpackage").forEach {
            write("fixture-jdk/$it", "Synthetic $it input")
        }
        write("fixture-jdk/readme.txt", "Synthetic vendor source-offer information\r\n")
        write("project-LICENSE", "MIT License\nSynthetic project copyright\n")
        write("settings.gradle.kts", "rootProject.name = \"runtime-legal-fixture\"\n")
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val logic = repository.resolve("buildSrc/build/libs/buildSrc.jar").toString().replace('\\', '/')
        write(
            "build.gradle.kts",
            """
            import de.heckenmann.visualagent.gradle.PrepareRuntimeLegalMaterials
            import de.heckenmann.visualagent.gradle.verifyPackagedRuntimeLegalMaterials
            buildscript { dependencies { classpath(files("$logic")) } }
            tasks.register<PrepareRuntimeLegalMaterials>("prepareRuntimeLegalMaterials") {
                jdkHome.set(layout.projectDirectory.dir("fixture-jdk"))
                projectLicense.set(layout.projectDirectory.file("project-LICENSE"))
                outputDirectory.set(layout.buildDirectory.dir("materials"))
            }
            tasks.register("verifyRuntimeFixture") { doLast {
                val report = verifyPackagedRuntimeLegalMaterials(
                    file("build/materials/common"), file("packaged-runtime"), file("fixture-jdk"), file("project-LICENSE"))
                file("verified.properties").writeText(report.entries.joinToString("\n") { (key, value) -> "${'$'}key=${'$'}value" })
            } }
            """.trimIndent(),
        )
        write("packaged-runtime/release", "JAVA_VERSION=\"27.0.1\"\nMODULES=\"java.base java.logging\"\n")
        write("packaged-runtime/bin/keytool", "Transformed keytool")
        write("packaged-runtime/lib/modules", "Reduced modules")
    }

    fun write(
        name: String,
        text: String,
    ) {
        val path = root.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    fun prepare() = fixtureGradle(root, listOf("prepareRuntimeLegalMaterials")).build()
}
