package de.heckenmann.visualagent.release.packaging

import de.heckenmann.visualagent.release.noticeSha256
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Gradle-prepared source packages and failure cases, replacing the Python test driver. */
@Tag("release-integration")
class LibrarySourcesIntegrationTest {
    @TempDir
    lateinit var temporary: Path
    private val repository = Path.of(System.getProperty("release.tools.repository"))
    private val output = repository.resolve("modules/desktop/build/reports/dependency-license/library-source-materials")
    private val released = repository.resolve("modules/desktop/build/reports/dependency-license/release-source-materials")
    private val document get() = Files.readString(repository.resolve("docs/licensing/source-delivery.md"))

    @Test
    fun `selected source JARs match documented exact versions and contain no class files`() {
        val expected =
            Regex("\\[([^\\]]+-sources\\.jar)\\].*?`([0-9a-f]{64})`")
                .findAll(document)
                .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(7, expected.size)
        assertTrue("jakarta.annotation-api-3.0.0-sources.jar" in expected)
        val archives = Files.list(output.resolve("archives")).use { paths -> paths.filter(Files::isRegularFile).toList() }
        assertEquals(expected, archives.associate { it.fileName.toString() to noticeSha256(it) })
        archives.forEach { path ->
            ZipFile(path.toFile()).use { archive ->
                val names =
                    archive
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toList()
                assertTrue(names.any { it.endsWith(".java") || it.endsWith(".kt") })
                assertFalse(names.any { it.endsWith(".class") })
            }
        }
        assertTrue(document.contains("no libffi native source tree"))
        assertTrue(
            document
                .replace(
                    Regex("\\s+"),
                    " ",
                ).contains("do not, on the current evidence, make that full archive a corresponding-source release blocker"),
        )
        assertTrue(document.contains("not the native Skia/ANGLE source trees"))
    }

    @Test
    fun `Zulu Java source package is pinned and its limitations remain explicit`() {
        val source = released.resolve("archives/zulu-jdk/zulu27-ca-src_27.0.1.0.3-5_amd64.deb")
        assertEquals("c803bd7fa69e94c8f1a7370e00dd98212480d8648d6e27eed06c27b97bd4b6b4", noticeSha256(source))
        for (text in listOf(
            "15,166 `.java` files",
            "not a substitute for",
            "does not contain native JDK source files",
        )) {
            assertTrue(document.contains(text))
        }
    }

    @Test
    fun `original legal files and source delivery scope survive byte for byte`() {
        val source = repository.resolve("docs/licensing/third-party")
        val expected = Files.walk(source).use { paths -> paths.filter(Files::isRegularFile).map { source.relativize(it) }.toList() }.toSet()
        val destination = output.resolve("legal")
        val actual =
            Files
                .walk(destination)
                .use { paths ->
                    paths.filter(Files::isRegularFile).map { destination.relativize(it) }.toList()
                }.toSet()
        assertEquals(expected, actual)
        expected.forEach { assertContentEquals(Files.readAllBytes(source.resolve(it)), Files.readAllBytes(destination.resolve(it))) }
        assertContentEquals(
            Files.readAllBytes(repository.resolve("docs/licensing/source-delivery.md")),
            Files.readAllBytes(output.resolve("source-delivery.md")),
        )
        assertTrue(
            Files
                .readString(
                    output.resolve("source-delivery.md"),
                ).replace(Regex("\\s+"), " ")
                .contains("not yet part of a recipient-facing source bundle"),
        )
    }

    @Test
    fun `missing required selected runtime component fails without partial source output`() {
        val init = temporary.resolve("missing-source.init.gradle.kts")
        val destination = temporary.resolve("output")
        Files.writeString(
            init,
            """
            allprojects { configurations.configureEach { exclude(group = "com.h2database", module = "h2") } }
            gradle.projectsEvaluated {
                rootProject.findProject(":desktop")?.layout?.buildDirectory?.set(java.io.File("${destination.toString().replace(
                '\\',
                '/',
            )}"))
            }
            """.trimIndent(),
        )
        val result =
            GradleRunner
                .create()
                .withProjectDir(repository.toFile())
                .withTestKitDir(Path.of(System.getProperty("release.tools.testkit")).toFile())
                .withArguments(
                    "-I",
                    init.toString(),
                    ":desktop:prepareLibrarySourceMaterials",
                    "--quiet",
                    "--offline",
                    "--gradle-user-home",
                    System.getProperty("release.tools.gradle.home"),
                    "-Dorg.gradle.java.home=${System.getProperty("java.home")}",
                ).buildAndFail()
        assertTrue(result.output.contains("Missing required selected-library source artifacts"), result.output)
        val materials = destination.resolve("reports/dependency-license/library-source-materials")
        assertFalse(Files.exists(materials) && Files.walk(materials).use { paths -> paths.anyMatch(Files::isRegularFile) })
    }
}
