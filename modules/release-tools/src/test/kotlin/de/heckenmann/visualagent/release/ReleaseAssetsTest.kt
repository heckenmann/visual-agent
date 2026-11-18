package de.heckenmann.visualagent.release

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Regression fixtures for local recipient assets and paginated upload verification. */
class ReleaseAssetsTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `complete candidate and matching paginated uploads pass`() {
        fixture()
        ReleaseAssets.verify(root)
        ReleaseAssets.verifyUploaded(root, uploads())
    }

    @Test
    fun `missing additional and empty files fail`() {
        fixture()
        val target = root.resolve("visual-agent-windows.msi")
        Files.delete(target)
        assertFails { ReleaseAssets.verify(root) }
        Files.writeString(target, "")
        assertFails { ReleaseAssets.verify(root) }
        Files.writeString(target, "package")
        val extra = Files.writeString(root.resolve("extra"), "extra")
        assertFails { ReleaseAssets.verify(root) }
        Files.delete(extra)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symbolic links requires developer privileges")
    fun `linked files fail`() {
        fixture()
        val target = root.resolve("visual-agent-windows.msi")
        Files.delete(target)
        Files.createSymbolicLink(target, root.resolve("visual-agent-linux-deb.deb"))
        assertFails { ReleaseAssets.verify(root) }
    }

    @Test
    fun `invalid and review only archives fail for either archive`() {
        for (name in ReleaseAssets.requiredNames.filter { it.endsWith(".zip") }) {
            fixture()
            Files.writeString(root.resolve(name), "not an archive")
            assertFails { ReleaseAssets.verify(root) }
            zip(root.resolve(name), "nested/REVIEW-ONLY.txt")
            assertFails { ReleaseAssets.verify(root) }
        }
    }

    @Test
    fun `changed missing duplicate and malformed uploads fail`() {
        fixture()
        val valid = uploads().toString()
        for (changed in listOf(
            valid.replace("uploaded", "starter"),
            valid.replace("sha256:", "sha512:"),
            valid.replace("\"size\":", "\"wrongSize\":"),
            "[]",
            "{}",
            "[{}]",
            "[$valid,$valid]",
        )) {
            assertFails { ReleaseAssets.verifyUploaded(root, Json.parseToJsonElement(changed)) }
        }
        val pages = uploads()
        assertFails { ReleaseAssets.verifyUploaded(root, JsonArray(pages + pages.first())) }
        assertFails { ReleaseAssets.verifyUploaded(root, JsonArray(pages.drop(1))) }
    }

    @Test
    fun `draft attachment verifies before upload and never publishes`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val script = Files.readString(repository.resolve("scripts/attach-release-draft.sh"))
        assertTrue(script.indexOf("scripts/release-tools.sh verify-assets") < script.indexOf("gh release upload"))
        assertTrue(script.contains("--draft"))
        assertTrue(!script.contains("draft=false"))
        assertTrue(!script.contains("release-approvals.json"))
    }

    private fun fixture() {
        ReleaseAssets.requiredNames.forEach { name ->
            val path = root.resolve(name)
            if (name.endsWith(".zip")) zip(path, "README.txt") else Files.writeString(path, name)
        }
    }

    private fun zip(
        path: Path,
        entry: String,
    ) {
        ZipOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(ZipEntry(entry))
            it.write("Recipient materials".toByteArray())
            it.closeEntry()
        }
    }

    private fun uploads() =
        buildJsonArray {
            ReleaseAssets.requiredNames.chunked(6).forEach { names ->
                add(
                    buildJsonArray {
                        names.forEach { name ->
                            val file = root.resolve(name)
                            add(
                                buildJsonObject {
                                    put("name", name)
                                    put("state", "uploaded")
                                    put("size", Files.size(file))
                                    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                                    put("digest", "sha256:" + digest.joinToString("") { "%02x".format(it) })
                                },
                            )
                        }
                    },
                )
            }
            add(buildJsonArray { add(buildJsonObject { put("name", "maintainer-notes.txt") }) })
        }
}
