package de.heckenmann.visualagent.release

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Original-byte, identity, and untrusted-metadata checks for supplemental legal evidence. */
class SupplementalEvidenceTest {
    @TempDir
    lateinit var root: Path
    private val content = "Original license\r\n".toByteArray()
    private lateinit var registry: Path
    private lateinit var valid: String

    @Test
    fun `separate component records cannot repeat an artifact coordinate`() {
        val data = Json.parseToJsonElement(valid).jsonObject
        val components = data.getValue("components").jsonArray
        val duplicated = JsonObject(data + ("components" to JsonArray(components + components)))
        Files.writeString(registry, duplicated.toString())
        assertFails { SupplementalEvidence.load(registry) }
    }

    @BeforeEach
    fun prepare() {
        registry = root.resolve("sources.json")
        Files.write(root.resolve("LICENSE.txt"), content)
        val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        valid =
            """
            {
              "schemaVersion":1,"redistributionApproved":false,
              "materials":[{"path":"LICENSE.txt","sha256":"$hash","url":"https://example.org/LICENSE"}],
              "components":[{"sourceArtifacts":{"example:lib:1":"${"a".repeat(64)}"},
              "upstreamCommit":"${"b".repeat(40)}","reviewStatus":"unreviewed",
              "remaining":["Source review"],"proposedLicense":"MIT","materialPaths":["LICENSE.txt"]}],
              "runtimePackages":[{"package":"example-runtime","runtimeSourceArtifact":"example:lib:1",
              "sourceArchivePath":"archives/source.tar.gz","linkedComponents":["example:lib:1"],
              "sourceArchives":[{"path":"archives/source.tar.gz","sha256":"${"a".repeat(64)}"}]}]
            }
            """.trimIndent()
        Files.writeString(registry, valid)
    }

    @Test
    fun `original bytes and exact artifact identity survive`() {
        val result = SupplementalEvidence.load(registry)
        assertEquals(setOf("example:lib:1"), result.keys)
        assertContentEquals(
            content,
            result
                .getValue("example:lib:1")
                .materials
                .single()
                .content,
        )
        assertEquals("a".repeat(64), result.getValue("example:lib:1").sourceArtifactSha256)
        val runtime = RuntimePackageEvidence.load(registry, result).getValue("example-runtime")
        assertEquals(mapOf("archives/source.tar.gz" to "a".repeat(64)), runtime.sourceArchives)
    }

    @Test
    fun `changed missing and empty original material fails`() {
        Files.writeString(root.resolve("LICENSE.txt"), "changed")
        assertFails { SupplementalEvidence.load(registry) }
        Files.writeString(root.resolve("LICENSE.txt"), "")
        assertFails { SupplementalEvidence.load(registry) }
        Files.delete(root.resolve("LICENSE.txt"))
        assertFails { SupplementalEvidence.load(registry) }
    }

    @Test
    fun `unsafe paths invalid identities approval and missing review fail`() {
        val variants =
            listOf(
                valid.replace("LICENSE.txt", "../LICENSE.txt"),
                valid.replace("LICENSE.txt", "/LICENSE.txt"),
                valid.replace("LICENSE.txt", "C:/LICENSE.txt"),
                valid.replace("LICENSE.txt", "dir\\\\LICENSE.txt"),
                valid.replace("https://example.org/LICENSE", "https://placeholder@localhost/LICENSE"),
                valid.replace("https://example.org/LICENSE", "http://example.org/LICENSE"),
                valid.replace("\"redistributionApproved\":false", "\"redistributionApproved\":true"),
                valid.replace("\"remaining\":[\"Source review\"]", "\"remaining\":[]"),
                valid.replace("b".repeat(40), "main"),
                valid.replace("\"materialPaths\":[\"LICENSE.txt\"]", "\"materialPaths\":[\"missing\"]"),
            )
        variants.forEach { text ->
            Files.writeString(registry, text)
            assertFails { SupplementalEvidence.load(registry) }
        }
    }

    @Test
    fun `duplicate keys are rejected at every nesting level including escaped spellings`() {
        for (text in listOf(
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            valid.replace("\"path\":\"LICENSE.txt\"", "\"path\":\"LICENSE.txt\",\"p\\u0061th\":\"LICENSE.txt\""),
            valid.replace("\"sourceArtifacts\":{", "\"sourceArtifacts\":{\"example:lib:1\":\"${"a".repeat(64)}\","),
        )) {
            Files.writeString(registry, text)
            assertFails { SupplementalEvidence.load(registry) }
        }
    }

    @Test
    fun `runtime mappings reject changed hashes duplicate components and unsafe archives`() {
        val supplements = SupplementalEvidence.load(registry)
        for (text in listOf(
            valid.replace("\"linkedComponents\":[\"example:lib:1\"]", "\"linkedComponents\":[\"example:lib:1\",\"example:lib:1\"]"),
            valid.replace("\"linkedComponents\":[\"example:lib:1\"]", "\"linkedComponents\":[\"missing\"]"),
            valid.replace("archives/source.tar.gz", "../source.tar.gz"),
            valid.replace("\"sha256\":\"${"a".repeat(64)}\"", "\"sha256\":\"${"c".repeat(64)}\""),
            valid.replace("\"package\":\"example-runtime\"", "\"package\":\"invalid/package\""),
        )) {
            Files.writeString(registry, text)
            assertFails { RuntimePackageEvidence.load(registry, supplements) }
        }
    }

    @Test
    fun `absent optional registries produce empty evidence`() {
        assertEquals(emptyMap(), SupplementalEvidence.load(null))
        assertEquals(emptyMap(), RuntimePackageEvidence.load(null, emptyMap()))
    }

    @Test
    fun `repository registry retains hash bound original materials and runtime mappings`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val path = repository.resolve("docs/licensing/third-party/sources.json")
        val supplements = SupplementalEvidence.load(path)
        assertTrue(supplements.isNotEmpty())
        assertTrue(supplements.values.all { it.materials.isNotEmpty() })
        val runtime = RuntimePackageEvidence.load(path, supplements).getValue("linux-appimage")
        assertEquals(
            setOf(
                "AppImage:type2-runtime:20251108",
                "AppImage:libfuse:3.15.0",
                "AppImage:squashfuse:0.5.2",
                "AppImage:musl:1.2.5",
                "mimalloc2:2.1.7-r0",
                "zstd:1.5.6",
                "zlib:1.4",
            ),
            runtime.linkedComponentEvidence.keys + "AppImage:type2-runtime:20251108",
        )
    }
}
