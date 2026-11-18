package de.heckenmann.visualagent.release.packaging

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the real version override and toolchain guard in isolated minimal Gradle projects. */
@Tag("release-integration")
class ReleaseVersionIntegrationTest {
    @TempDir
    lateinit var root: Path
    private val repository = Path.of(System.getProperty("release.tools.repository"))

    @Test
    fun `local manual tagged zero major and prerelease builds share exact catalog versions`() {
        for ((tag, expected) in listOf(
            null to "1.0.0",
            "" to "1.0.0",
            "v2.3.4" to "2.3.4",
            "v0.1.0" to "0.1.0",
            "2.3.4-beta.1+build.7" to "2.3.4-beta.1+build.7",
        )) {
            val fixture = versionFixture()
            val environment = if (tag == null) emptyMap() else mapOf("ORG_GRADLE_PROJECT_releaseTag" to tag)
            val result = fixtureGradle(fixture, listOf("printVersions"), environment).build()
            assertEquals(
                listOf(":=$expected", ":child=$expected"),
                result.output
                    .lineSequence()
                    .filter {
                        it.startsWith(":=") ||
                            it.startsWith(":child=")
                    }.toList(),
            )
        }
    }

    @Test
    fun `invalid semver prerelease and installer overflow fail before packaging`() {
        for ((tag, message) in listOf(
            "v1.02.3" to "Release tag must be a semantic version",
            "v1.2.3-beta.01" to "Release tag must be a semantic version",
            "v256.2.3" to "Release version exceeds native installer limits",
        )) {
            val result =
                fixtureGradle(
                    versionFixture(),
                    listOf("printVersions"),
                    mapOf("ORG_GRADLE_PROJECT_releaseTag" to tag),
                ).buildAndFail()
            assertTrue(result.output.contains(message), result.output)
        }
    }

    @Test
    fun `actual toolchain guard accepts JDK27 with release24 and rejects divergent releases`() {
        val build = Files.readString(repository.resolve("build.gradle.kts"))
        val guard = build.substringAfter("val verifyJavaToolchains =").substringBefore("\nlicenseReport {")
        for (release in listOf(24, 25)) {
            val project = Files.createTempDirectory(root, "toolchain-")
            write(project, "settings.gradle.kts", "rootProject.name = \"toolchain-guard-fixture\"\n")
            write(project, "gradle/libs.versions.toml", "[versions]\njava-toolchain = \"27\"\n")
            write(
                project,
                "build.gradle.kts",
                """
                import org.gradle.api.tasks.testing.Test
                plugins { java }
                val javaBytecodeVersion = 24
                java { toolchain { languageVersion.set(JavaLanguageVersion.of(27)) } }
                tasks.withType<JavaCompile>().configureEach { options.release.set($release) }
                val verifyJavaToolchains = $guard
                """.trimIndent(),
            )
            val runner = fixtureGradle(project, listOf("verifyJavaToolchains"))
            if (release == 24) runner.build() else assertTrue(runner.buildAndFail().output.contains("has a different bytecode release"))
        }
    }

    private fun versionFixture(): Path {
        val project = Files.createTempDirectory(root, "version-")
        write(
            project,
            "gradle/release-version.settings.gradle.kts",
            Files.readString(repository.resolve("gradle/release-version.settings.gradle.kts")),
        )
        write(project, "gradle/libs.versions.toml", "[versions]\nvisual-agent = \"1.0.0\"\n")
        write(
            project,
            "settings.gradle.kts",
            "rootProject.name = \"fixture\"\napply(from = \"gradle/release-version.settings.gradle.kts\")\ninclude(\":child\")\n",
        )
        write(project, "child/build.gradle.kts", "version = libs.versions.visual.agent.get()\n")
        write(
            project,
            "build.gradle.kts",
            """
            version = libs.versions.visual.agent.get()
            tasks.register("printVersions") { doLast { allprojects.forEach { println("${'$'}{it.path}=${'$'}{it.version}") } } }
            """.trimIndent(),
        )
        return project
    }

    private fun write(
        project: Path,
        name: String,
        text: String,
    ) {
        val path = project.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }
}
