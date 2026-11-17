package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Source binding, command cleanup, and option validation for AppImage notice collection. */
class AppImageNoticeEvidenceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `embedded runtime evidence requires pinned hash version and matching prefix`() {
        val bytes = ByteArray(39)
        bytes[8] = 65
        bytes[9] = 73
        bytes[10] = 2
        val runtime = Files.write(root.resolve("runtime"), bytes)
        val generated = bytes.copyOf().also { (16 until 32).forEach { i -> it[i] = 1 } }
        val packaged = Files.write(root.resolve("application.AppImage"), generated + byteArrayOf(1, 2, 3))
        val archive = Files.writeString(root.resolve("runtime.tar.gz"), "source archive")
        val original = "Original runtime license\r\n".toByteArray()
        val originalPath = Files.write(root.resolve("original-license"), original)
        val material =
            SupplementalMaterial(
                buildJsonObject {
                    put("path", "runtime/LICENSE")
                    put("sha256", noticeSha256(originalPath))
                },
                original,
            )
        val source =
            SupplementalComponent(
                noticeSha256(archive),
                JsonPrimitive("MIT"),
                "a".repeat(40),
                JsonArray(listOf(JsonPrimitive("Review"))),
                listOf(material),
            )
        val declaration =
            RuntimePackageEvidence(
                buildJsonObject { put("sourceArchivePath", "archives/runtime.tar.gz") },
                source,
                mapOf("example:static-library:1" to source.copy(sourceArtifactSha256 = "b".repeat(64))),
                mapOf("archives/runtime.tar.gz" to noticeSha256(archive)),
            )
        val inputs = AppImageNoticeInputs(packaged, runtime, archive)

        fun query(args: List<String>): String =
            if (args.first() == "readelf") {
                " [ 4] .digest_md5 PROGBITS 0000000000000010 000010 000010 00 WA 0 0 1\n"
            } else {
                "https://github.com/AppImage/type2-runtime/commit/aaaaaaa"
            }
        val report = retainAppImageEvidence(inputs, root, longArrayOf(0), declaration, ::query)
        assertEquals("true", report.getValue("embeddedPrefixVerified").jsonPrimitive.content)
        assertEquals(noticeSha256(packaged), report.text("packageSha256"))
        assertContentEquals(original, Files.readAllBytes(root.resolve("appimage-runtime-legal/example-static-library-1/LICENSE")))
        assertFails {
            retainAppImageEvidence(inputs, root, longArrayOf(0), declaration) { args ->
                if (args.first() == "readelf") query(args) else "wrong commit"
            }
        }
        Files.writeString(archive, "changed source")
        assertFails { retainAppImageEvidence(inputs, root, longArrayOf(0), declaration, ::query) }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX command execution fixtures")
    fun `command failures and timeouts clean their exact scratch directories`() {
        assertEquals("outerror", runEvidenceCommand(listOf("bash", "-c", "printf out; printf error >&2"), root))
        assertFails { runEvidenceCommand(listOf("bash", "-c", "exit 7"), root) }
        assertFails { runEvidenceCommand(listOf("bash", "-c", "sleep 30"), root, 1) }
        assertTrue(Files.list(root).use { it.findAny().isEmpty })
    }

    @Test
    fun `notice options retain equals syntax and reject missing duplicate unknown and partial inputs`() {
        val base =
            listOf(
                "--package-root=package",
                "--inventory",
                "inventory.csv",
                "--output",
                "output",
                "--license-report",
                "licenses.json",
                "--project-license",
                "LICENSE",
            )
        assertEquals(Path.of("package"), noticeRequest(base).packageRoot)
        for (args in listOf(
            emptyList(),
            base + listOf("--unknown", "value"),
            base + listOf("--output", "duplicate"),
            base + listOf("--appimage-runtime", "runtime"),
            base + listOf("--supplements"),
        )) {
            assertFails { noticeRequest(args) }
        }
    }
}
