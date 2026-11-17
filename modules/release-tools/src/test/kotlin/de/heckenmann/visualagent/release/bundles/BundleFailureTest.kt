package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.EvidenceJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFails
import kotlin.test.assertFalse

/** Fail-closed archive cases migrated from the legacy material-bundle suite. */
class BundleFailureTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `missing archives scope and pinned runtime sources stop archive creation`() {
        val changes: List<(BundleFixture) -> Unit> =
            listOf(
                {
                    Files.delete(it.sources.resolve("archives/vendor/source.tar.gz"))
                    Files.delete(it.sources.resolve("archives/appimage-runtime/runtime.tar.gz"))
                },
                { Files.delete(it.sources.resolve("source-delivery.md")) },
                { Files.writeString(it.sources.resolve("archives/appimage-runtime/runtime.tar.gz"), "changed") },
            )
        changes.forEach { change -> reject(change) }
    }

    @Test
    fun `missing and unexpected platform directories fail closed`() {
        reject { Files.move(it.notices.resolve("windows"), it.root.resolve("moved-platform")) }
        reject { fixture -> fixture.write(fixture.notices.resolve("unexpected-platform/manifest.json"), "{}") }
    }

    @Test
    fun `missing runtime AppImage evidence approval and project license mismatches fail closed`() {
        for (field in listOf("runtimeLegal", "appImageRuntime")) {
            reject { mutate(it) { manifest -> JsonObject(manifest - field) } }
        }
        reject { mutate(it) { manifest -> JsonObject(manifest + ("redistributionApproved" to JsonPrimitive(true))) } }
        reject { mutate(it) { manifest -> JsonObject(manifest + ("projectLicenseSha256" to JsonPrimitive("0".repeat(64)))) } }
    }

    @Test
    fun `changed and unmanifested runtime or notice files fail closed`() {
        for (path in listOf(
            "runtime-legal/vendor/readme.txt",
            "artifacts/package-hash/LICENSE",
            "appimage-runtime-legal/example-static-1/LICENSE",
        )) {
            reject { Files.writeString(it.notices.resolve("linux-appimage/$path"), "changed") }
        }
        reject { it.write(it.notices.resolve("linux-appimage/runtime-legal/extra.txt"), "unmanifested") }
        reject { it.write(it.notices.resolve("linux-appimage/appimage-runtime-legal/extra.txt"), "unmanifested") }
    }

    @Test
    fun `changed or undeclared curated material fails closed`() {
        reject { Files.writeString(it.project.resolve("docs/licensing/third-party/vendor/LICENSE"), "changed") }
        reject { fixture ->
            val path = fixture.project.resolve("docs/licensing/third-party/sources.json")
            Files.writeString(path, JsonObject(EvidenceJson.read(path) + ("materials" to JsonArray(emptyList()))).toString())
        }
    }

    @Test
    fun `output cannot be placed inside source materials`() {
        val fixture = BundleFixture(root)
        assertFails { ReleaseMaterialBundles.build(fixture.request.copy(outputDirectory = fixture.sources.resolve("output"))) }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symbolic links requires developer privileges")
    fun `linked source root and original source file fail closed`() {
        val fixture = BundleFixture(root)
        val linked = Files.createSymbolicLink(fixture.root.resolve("linked-root"), fixture.sources)
        assertFails { ReleaseMaterialBundles.build(fixture.request.copy(sourceMaterials = linked)) }
        Files.createSymbolicLink(
            fixture.sources.resolve("archives/vendor/linked.tar.gz"),
            fixture.sources.resolve("archives/vendor/source.tar.gz"),
        )
        assertFails { ReleaseMaterialBundles.build(fixture.request) }
    }

    private fun mutate(
        fixture: BundleFixture,
        change: (JsonObject) -> JsonObject,
    ) {
        val path = fixture.notices.resolve("linux-appimage/manifest.json")
        Files.writeString(path, change(EvidenceJson.read(path)).toString())
    }

    private fun reject(change: (BundleFixture) -> Unit) {
        val fixture = BundleFixture(root)
        change(fixture)
        assertFails { ReleaseMaterialBundles.build(fixture.request) }
        assertFalse(Files.exists(fixture.output.resolve("visual-agent-legal.zip")))
        assertFalse(Files.exists(fixture.output.resolve("visual-agent-corresponding-sources.zip")))
    }
}
