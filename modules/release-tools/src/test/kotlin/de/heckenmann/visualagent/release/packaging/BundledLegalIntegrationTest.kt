package de.heckenmann.visualagent.release.packaging

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Functional Gradle verification for original bundled legal files and native packaging inputs. */
@Tag("release-integration")
class BundledLegalIntegrationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `native and JAR verification preserve original bytes`() {
        val fixture = BundledLegalFixture(root)
        val native = fixture.root.resolve("native/legal/third-party/LICENSE.txt")
        val nativeBefore = Files.readAllBytes(native)
        fixtureGradle(fixture.root, listOf("verifyNative")).build()
        assertContentEquals(nativeBefore, Files.readAllBytes(native))
        val jar = fixture.root.resolve("app.jar")
        val jarBefore = Files.readAllBytes(jar)
        fixtureGradle(fixture.root, listOf("verifyJar")).build()
        assertContentEquals(jarBefore, Files.readAllBytes(jar))
    }

    @Test
    fun `missing or changed native notices and missing registry fail closed`() {
        for (change in listOf("missing", "changed", "registry")) {
            val fixture = BundledLegalFixture(root)
            val notice = fixture.root.resolve("native/legal/third-party/LICENSE.txt")
            when (change) {
                "missing" -> Files.delete(notice)
                "changed" -> Files.writeString(notice, "Changed")
                else -> Files.delete(fixture.upstream.resolve("sources.json"))
            }
            val result = fixtureGradle(fixture.root, listOf("verifyNative")).buildAndFail()
            assertTrue(result.output.contains(if (change == "registry") "registry is missing" else "missing or changed"), result.output)
        }
    }

    @Test
    fun `missing project license changed notice and duplicate JAR entries fail closed`() {
        for (change in listOf("missing", "changed", "duplicate")) {
            val fixture = BundledLegalFixture(root)
            val project = "META-INF/visual-agent/legal/visual-agent/LICENSE"
            val notice = "META-INF/visual-agent/legal/third-party/LICENSE.txt"
            when (change) {
                "missing" -> fixture.entries.removeAll { it.first == project }
                "changed" -> {
                    fixture.entries.removeAll { it.first == notice }
                    fixture.entries.add(notice to "Changed".toByteArray())
                }
                else -> fixture.entries.add(notice to "Ambiguous".toByteArray())
            }
            fixture.jar()
            val result = fixtureGradle(fixture.root, listOf("verifyJar")).buildAndFail()
            assertTrue(
                result.output.contains(
                    if (change ==
                        "duplicate"
                    ) {
                        "Duplicate bundled legal entry"
                    } else {
                        "missing or changed"
                    },
                ),
                result.output,
            )
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symbolic links requires developer privileges")
    fun `linked native notice fails even if it targets identical upstream content`() {
        val fixture = BundledLegalFixture(root)
        val notice = fixture.root.resolve("native/legal/third-party/LICENSE.txt")
        Files.delete(notice)
        Files.createSymbolicLink(notice, fixture.upstream.resolve("LICENSE.txt"))
        assertTrue(fixtureGradle(fixture.root, listOf("verifyNative")).buildAndFail().output.contains("linked"))
    }

    @Test
    fun `generated legal resources invalidate native packaging inputs`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val init = root.resolve("native-inputs.init.gradle.kts")
        Files.writeString(
            init,
            """
            gradle.projectsEvaluated { rootProject.findProject(":desktop")?.let { desktop ->
                desktop.tasks.register("verifyLegalInputTracking") {
                    dependsOn("prepareDistributionLegalResources")
                    doLast {
                        val expected = desktop.layout.buildDirectory.dir("generated/distribution-legal-resources").get().asFile.canonicalFile
                        val packager = desktop.tasks.getByName("createDistributable")
                        val required = expected.walkTopDown().filter { it.isFile }.map { it.canonicalFile }.toSet()
                        val tracked = packager.inputs.files.files.map { it.canonicalFile }.toSet()
                        check(required.isNotEmpty() && tracked.containsAll(required)) { "Legal resources must invalidate native packaging" }
                    }
                }
            } }
            """.trimIndent(),
        )
        fixtureGradle(repository, listOf("-I", init.toString(), ":desktop:verifyLegalInputTracking")).build()
    }
}
