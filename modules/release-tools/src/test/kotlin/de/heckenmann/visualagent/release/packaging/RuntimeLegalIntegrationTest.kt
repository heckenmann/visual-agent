package de.heckenmann.visualagent.release.packaging

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Exercises the production runtime legal tasks through real Gradle executions. */
@Tag("release-integration")
class RuntimeLegalIntegrationTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `retains original notices and bounded metadata without host paths or executables`() {
        val fixture = RuntimeLegalFixture(temporary)
        fixture.prepare()
        assertContentEquals(
            Files.readAllBytes(fixture.jdk.resolve("readme.txt")),
            Files.readAllBytes(fixture.output.resolve("vendor/readme.txt")),
        )
        listOf("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION").forEach {
            assertContentEquals(
                Files.readAllBytes(fixture.jdk.resolve("legal/java.base/$it")),
                Files.readAllBytes(fixture.output.resolve("legal/java.base/$it")),
            )
        }
        val metadata = Files.readString(fixture.output.resolve("source-jdk.properties"))
        assertTrue(metadata.contains("JAVA_RUNTIME_VERSION=\"27.0.1+9\""))
        listOf("PRIVATE_HOST_PATH", "/private/not-for-distribution", fixture.root.toString()).forEach { assertFalse(metadata.contains(it)) }
        assertTrue(
            Files
                .readString(
                    fixture.output.resolve("source-inputs.sha256"),
                ).contains("${hash(fixture.jdk.resolve("bin/keytool"))}  bin/keytool"),
        )
        assertFalse(Files.exists(fixture.output.resolve("lib/modules")))
        assertTrue(Files.readString(fixture.output.resolve("README.txt")).contains("not complete corresponding source"))
        assertContentEquals(
            Files.readAllBytes(fixture.root.resolve("project-LICENSE")),
            Files.readAllBytes(fixture.root.resolve("build/materials/common/legal/visual-agent/LICENSE")),
        )
    }

    @Test
    fun `changed inputs invalidate materials and removed vendor notices leave no stale output`() {
        val fixture = RuntimeLegalFixture(temporary)
        fixture.prepare()
        val before = Files.readString(fixture.output.resolve("source-inputs.sha256"))
        fixture.write("fixture-jdk/bin/keytool", "Changed toolchain input")
        Files.delete(fixture.jdk.resolve("readme.txt"))
        fixture.prepare()
        val after = Files.readString(fixture.output.resolve("source-inputs.sha256"))
        assertNotEquals(before, after)
        assertFalse(after.contains("  readme.txt\n"))
        assertFalse(Files.exists(fixture.output.resolve("vendor/readme.txt")))
    }

    @Test
    fun `records source and packaged transformations separately`() {
        val fixture = RuntimeLegalFixture(temporary)
        fixture.prepare()
        fixtureGradle(fixture.root, listOf("verifyRuntimeFixture")).build()
        val report = Files.readString(fixture.root.resolve("verified.properties"))
        val source = hash(fixture.jdk.resolve("bin/keytool"))
        val packaged = hash(fixture.root.resolve("packaged-runtime/bin/keytool"))
        assertNotEquals(source, packaged)
        listOf(
            "source.keytool.sha256=$source",
            "packaged.keytool.sha256=$packaged",
            "source.modules.list=java.base,java.compiler,java.logging,java.sql",
            "packaged.modules.list=java.base,java.logging",
        ).forEach {
            assertTrue(report.contains(it), report)
        }
        assertFalse(report.contains(fixture.root.toString()))
    }

    @Test
    fun `rejects missing module inventory changed notices changed source and incomplete provenance`() {
        listOf(
            "Missing MODULES runtime metadata" to
                { f: RuntimeLegalFixture -> f.write("packaged-runtime/release", "JAVA_VERSION=\"27.0.1\"\n") },
            "Retained JDK notice changed" to
                { f: RuntimeLegalFixture -> Files.writeString(f.output.resolve("legal/java.base/LICENSE"), "Tampered") },
            "Source JDK changed" to { f: RuntimeLegalFixture -> f.write("fixture-jdk/bin/keytool", "Unrecorded change") },
            "Incomplete or divergent" to { f: RuntimeLegalFixture ->
                val manifest = f.output.resolve("source-inputs.sha256")
                Files.writeString(
                    manifest,
                    Files.readAllLines(manifest).filterNot { it.endsWith("  readme.txt") }.joinToString("\n", postfix = "\n"),
                )
            },
        ).forEach { (message, mutate) ->
            val fixture = RuntimeLegalFixture(temporary)
            fixture.prepare()
            mutate(fixture)
            failure(fixture, "verifyRuntimeFixture", message)
        }
    }

    @Test
    fun `rejects missing empty or injected required metadata and licenses`() {
        listOf(
            "Missing or escaping source-JDK file" to { f: RuntimeLegalFixture -> Files.delete(f.jdk.resolve("legal/java.base/LICENSE")) },
            "Empty source-JDK legal/input file: legal/java.base/LICENSE" to
                { f: RuntimeLegalFixture -> f.write("fixture-jdk/legal/java.base/LICENSE", "") },
            "JAVA_RUNTIME_VERSION" to
                { f: RuntimeLegalFixture -> f.write("fixture-jdk/release", "JAVA_VERSION=\"27\"\nIMPLEMENTOR=\"Synthetic Vendor\"\n") },
            "Invalid control character" to
                { f: RuntimeLegalFixture ->
                    Files.writeString(
                        f.jdk.resolve("release"),
                        "IMPLEMENTOR=Vendor\\nPRIVATE_HOST_PATH=not-for-distribution\n",
                        java.nio.file.StandardOpenOption.APPEND,
                    )
                },
        ).forEach { (message, mutate) ->
            val fixture = RuntimeLegalFixture(temporary)
            mutate(fixture)
            failure(fixture, "prepareRuntimeLegalMaterials", message)
        }
    }

    @Test
    fun `preserves empty optional legal placeholders and Windows executable names`() {
        val fixture = RuntimeLegalFixture(temporary)
        fixture.write("fixture-jdk/legal/java.management.rmi/LICENSE", "")
        listOf("keytool", "jlink", "jpackage").forEach { Files.move(fixture.jdk.resolve("bin/$it"), fixture.jdk.resolve("bin/$it.exe")) }
        fixture.prepare()
        val retained = fixture.output.resolve("legal/java.management.rmi/LICENSE")
        assertTrue(Files.isRegularFile(retained))
        assertContentEquals(byteArrayOf(), Files.readAllBytes(retained))
        val manifest = Files.readString(fixture.output.resolve("source-inputs.sha256"))
        listOf("${hash(retained)}  legal/java.management.rmi/LICENSE", "  bin/keytool.exe\n", "  bin/jpackage.exe\n").forEach {
            assertTrue(manifest.contains(it), manifest)
        }
    }

    @Test
    fun `rejects escaping symlinks and materializes internal vendor links`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val fixture = RuntimeLegalFixture(temporary)
        fixture.write("private.txt", "Must not be copied")
        Files.createSymbolicLink(fixture.jdk.resolve("legal/java.base/escape.txt"), fixture.root.resolve("private.txt"))
        failure(fixture, "prepareRuntimeLegalMaterials", "escaping source-JDK file")
        Files.delete(fixture.jdk.resolve("legal/java.base/escape.txt"))
        Files.createDirectories(fixture.jdk.resolve("legal/java.compiler"))
        Files.createSymbolicLink(fixture.jdk.resolve("legal/java.compiler/LICENSE"), Path.of("../java.base/LICENSE"))
        fixture.prepare()
        val retained = fixture.output.resolve("legal/java.compiler/LICENSE")
        assertFalse(Files.isSymbolicLink(retained))
        assertContentEquals(Files.readAllBytes(fixture.jdk.resolve("legal/java.base/LICENSE")), Files.readAllBytes(retained))
    }

    private fun failure(
        fixture: RuntimeLegalFixture,
        task: String,
        message: String,
    ) {
        val result = fixtureGradle(fixture.root, listOf(task)).buildAndFail()
        assertTrue(result.output.contains(message), result.output)
    }

    private fun hash(path: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") {
            "%02x".format(it)
        }
}
