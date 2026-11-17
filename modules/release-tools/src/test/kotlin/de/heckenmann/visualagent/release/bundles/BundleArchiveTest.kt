package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.noticeSha256
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonObject
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Stored ZIP reproducibility, original-byte preservation, and curated evidence regression checks. */
class BundleArchiveTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `archives are reproducible with fixed dates permissions manifests and original bytes`() {
        val original = "Original license\r\n".toByteArray()
        val file = Files.write(root.resolve("LICENSE"), original)
        val entries = listOf(BundleEntry("LICENSE", source = file), BundleEntry("generated.txt", bytes = "generated\n".toByteArray()))
        val first = root.resolve("one.zip")
        val second = root.resolve("two.zip")
        writeBundleArchive(first, entries, "Review only\n")
        writeBundleArchive(second, entries, "Review only\n")
        assertContentEquals(Files.readAllBytes(first), Files.readAllBytes(second))
        ZipFile.builder().setPath(first).get().use { archive ->
            val names =
                archive.entries
                    .asSequence()
                    .map { it.name }
                    .toList()
            assertEquals(listOf("LICENSE", "generated.txt", "MANIFEST.sha256", "REVIEW-ONLY.txt"), names)
            for (entry in archive.entries.asSequence()) {
                assertEquals(0, entry.method)
                assertEquals(0x81A4, entry.unixMode)
                assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0), entry.timeLocal)
            }
            assertContentEquals(original, archive.getInputStream(archive.getEntry("LICENSE")).use { it.readAllBytes() })
            val manifest = archive.getInputStream(archive.getEntry("MANIFEST.sha256")).use { it.readAllBytes().toString(Charsets.UTF_8) }
            assertEquals(entries.joinToString("") { "${it.sha256()}  ${it.name}\n" }, manifest)
        }
    }

    @Test
    fun `failed input preserves existing output and leaves no temporary archive`() {
        val output = Files.writeString(root.resolve("archive.zip"), "previous archive")
        assertFails { writeBundleArchive(output, listOf(BundleEntry("missing", source = root.resolve("missing"))), null) }
        assertEquals("previous archive", Files.readString(output))
        assertEquals(listOf("archive.zip"), Files.list(root).use { it.map { path -> path.fileName.toString() }.toList() })
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symbolic links requires developer privileges")
    fun `linked directory roots and files are rejected`() {
        val materials = Files.createDirectory(root.resolve("materials"))
        val source = Files.writeString(materials.resolve("license"), "license")
        val alias = Files.createSymbolicLink(root.resolve("alias"), materials)
        assertFails { bundleFiles(alias) }
        Files.createSymbolicLink(materials.resolve("linked"), source)
        assertFails { bundleFiles(materials) }
    }

    @Test
    fun `repository curated evidence validates pinned runtime source maps`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val legal = repository.resolve("docs/licensing/third-party")
        val mappings = curatedBundleEvidence(bundleFiles(legal))
        val runtime = mappings.getValue("linux-appimage")
        assertEquals("AppImage:type2-runtime:20251108", runtime.text("runtimeSourceArtifact"))
        assertTrue(runtime.getValue("sourceArchiveFiles").jsonObject.isNotEmpty())
        assertEquals("f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba", runtime.text("runtimeSourceArtifactSha256"))
    }

    @Test
    fun `curated material hashes duplicates and undeclared references fail closed`() {
        val file = Files.writeString(root.resolve("LICENSE"), "license")
        val hash = noticeSha256(file)
        val material = """{"path":"LICENSE","sha256":"$hash"}"""
        val valid = """{"materials":[$material],"components":[]}"""
        val registry = root.resolve("sources.json")
        Files.writeString(registry, valid)
        assertEquals(emptyMap(), curatedBundleEvidence(bundleFiles(root)))
        for (invalid in listOf(
            valid.replace(hash, "a".repeat(64)),
            valid.replace("[$material]", "[$material,$material]"),
            valid.replace("\"components\":[]", "\"components\":[{\"materialPaths\":[\"missing\"]}]"),
        )) {
            Files.writeString(registry, invalid)
            assertFails { curatedBundleEvidence(bundleFiles(root)) }
        }
    }
}
