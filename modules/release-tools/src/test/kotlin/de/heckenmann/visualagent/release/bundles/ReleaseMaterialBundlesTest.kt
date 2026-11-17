package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.EvidenceJson
import de.heckenmann.visualagent.release.bundleRequest
import de.heckenmann.visualagent.release.noticeSha256
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Full review/recipient generation and deterministic recipient payload selection. */
class ReleaseMaterialBundlesTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `review archives retain all evidence source scope and manifest markers reproducibly`() {
        val fixture = BundleFixture(root)
        val first = ReleaseMaterialBundles.build(fixture.request)
        val hashes = listOf(noticeSha256(first.first), noticeSha256(first.second))
        val second = ReleaseMaterialBundles.build(fixture.request)
        assertEquals(hashes, listOf(noticeSha256(second.first), noticeSha256(second.second)))
        ZipFile.builder().setPath(first.first).get().use { archive ->
            val names =
                archive.entries
                    .asSequence()
                    .map { it.name }
                    .toSet()
            assertTrue("REVIEW-ONLY.txt" in names)
            assertTrue("licensing/third-party/historical/LICENSE" in names)
            for (platform in bundleNoticePlatforms) assertTrue("package-notice-evidence/$platform/manifest.json" in names)
            assertEquals(
                "Project license",
                archive.getInputStream(archive.getEntry("LICENSE")).use { it.readAllBytes().toString(Charsets.UTF_8) },
            )
        }
        ZipFile.builder().setPath(first.second).get().use { archive ->
            assertEquals(
                setOf(
                    "source-materials/archives/vendor/source.tar.gz",
                    "source-materials/archives/appimage-runtime/runtime.tar.gz",
                    "source-materials/legal/vendor/LICENSE",
                    "source-materials/source-delivery.md",
                    "MANIFEST.sha256",
                    "REVIEW-ONLY.txt",
                ),
                archive.entries
                    .asSequence()
                    .map { it.name }
                    .toSet(),
            )
        }
    }

    @Test
    fun `recipient archives omit historical review records and include selected original platform materials`() {
        val fixture = BundleFixture(root)
        val result = ReleaseMaterialBundles.build(fixture.request.copy(recipientCandidate = true))
        ZipFile.builder().setPath(result.first).get().use { archive ->
            val names =
                archive.entries
                    .asSequence()
                    .map { it.name }
                    .toSet()
            assertTrue("README-LICENSES.txt" in names)
            assertTrue("third-party/vendor/LICENSE" in names)
            assertTrue("third-party/Apache-2.0.txt" in names)
            for (excluded in listOf(
                "REVIEW-ONLY.txt",
                "third-party/historical/LICENSE",
                "third-party/sources.json",
                "licensing/README.md",
            )) {
                assertFalse(
                    excluded in names,
                )
            }
            for (platform in bundleNoticePlatforms) assertTrue("platforms/$platform/java-runtime/vendor/readme.txt" in names)
            assertTrue("platforms/linux-appimage/appimage-runtime/example-static-1/LICENSE" in names)
        }
        ZipFile.builder().setPath(result.second).get().use { archive ->
            assertEquals(
                setOf(
                    "README-SOURCES.txt",
                    "source-materials/archives/vendor/source.tar.gz",
                    "source-materials/archives/appimage-runtime/runtime.tar.gz",
                    "MANIFEST.sha256",
                ),
                archive.entries
                    .asSequence()
                    .map { it.name }
                    .toSet(),
            )
        }
    }

    @Test
    fun `downloaded platform folder names normalize to the same archive paths`() {
        val fixture = BundleFixture(root)
        bundleNoticePlatforms.forEach { Files.move(fixture.notices.resolve(it), fixture.notices.resolve("license-notices-$it")) }
        val result = ReleaseMaterialBundles.build(fixture.request)
        ZipFile.builder().setPath(result.first).get().use { archive ->
            bundleNoticePlatforms.forEach { assertTrue(archive.getEntry("package-notice-evidence/$it/manifest.json") != null) }
        }
    }

    @Test
    fun `repeated coordinates retain distinct notice bytes keyed by content hash`() {
        val fixture = BundleFixture(root)
        val platform = fixture.notices.resolve("linux-appimage")
        val second =
            fixture.write(
                platform.resolve("artifacts/another-package-hash/LICENSE"),
                "second license for repeated component coordinate",
            )
        val path = platform.resolve("manifest.json")
        val manifest = EvidenceJson.read(path)
        val components = manifest.getValue("components").jsonArray
        val original = components.single().jsonObject
        val notice =
            JsonObject(
                mapOf(
                    "path" to JsonPrimitive("artifacts/another-package-hash/LICENSE"),
                    "sha256" to JsonPrimitive(noticeSha256(second)),
                ),
            )
        val duplicate = JsonObject(original + ("notices" to JsonArray(listOf(notice))))
        Files.writeString(path, JsonObject(manifest + ("components" to JsonArray(components + duplicate))).toString())
        val result = ReleaseMaterialBundles.build(fixture.request.copy(recipientCandidate = true))
        ZipFile.builder().setPath(result.first).get().use { archive ->
            val name = "platforms/linux-appimage/notices/example_component_1/${noticeSha256(second)}/LICENSE"
            assertContentEquals(Files.readAllBytes(second), archive.getInputStream(archive.getEntry(name)).use { it.readAllBytes() })
            assertEquals(
                2,
                archive.entries.asSequence().count { it.name.startsWith("platforms/linux-appimage/notices/example_component_1/") },
            )
        }
    }

    @Test
    fun `bundle command retains named options and recipient flag`() {
        val request =
            bundleRequest(
                listOf(
                    "--source-materials=sources",
                    "--notice-evidence",
                    "notices",
                    "--output-directory",
                    "output",
                    "--recipient-candidate",
                ),
            )
        assertEquals(Path.of("."), request.projectRoot)
        assertTrue(request.recipientCandidate)
    }
}
