package de.heckenmann.visualagent.release.packaging

import de.heckenmann.visualagent.release.SupplementalEvidence
import de.heckenmann.visualagent.release.noticeSha256
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source-delivery boundaries and AppImage workflow contracts independent of package generation. */
class SourceWorkflowTest {
    private val repository = Path.of(System.getProperty("release.tools.repository"))

    @Test
    fun `native macOS DNS evidence stays historical and unreviewed`() {
        val delivery = Files.readString(repository.resolve("docs/licensing/source-delivery.md")).replace(Regex("\\s+"), " ")
        for (text in listOf(
            "historical review evidence",
            "not included in the new release source bundle",
            "98a33e5adbbe58cb084ed4ca61350fa0a6311f4f8e54bc8cd823a402d0609c42",
        )) {
            assertTrue(delivery.contains(text))
        }
        val root = repository.resolve("docs/licensing/third-party/netty-4.2.20.Final")
        assertEquals("f0bdf5682bf317b1f85c80ed32cb4c7bf77b5a231dee485aa92d983673b7c58f", noticeSha256(root.resolve("LICENSE.dnsinfo.txt")))
        assertEquals("2d0c12edaaf8d31fec6796fef99291b12e833b6bef28100975120a53d209e06d", noticeSha256(root.resolve("APSL-2.0.txt")))
        assertTrue(Files.readString(root.resolve("APSL-2.0.txt")).contains("APPLE PUBLIC SOURCE LICENSE"))
        val component =
            SupplementalEvidence
                .load(root.parent.resolve("sources.json"))
                .getValue("io.netty:netty-resolver-dns-native-macos:4.2.20.Final")
        assertEquals(3, component.materials.size)
        assertTrue(component.materials.drop(1).all { it.content.toString(Charsets.UTF_8).contains("Apple Public Source License") })
        assertTrue(component.remaining.jsonArray.isNotEmpty())
    }

    @Test
    fun `release rebuilds verifies and packages the pinned AppImage runtime through Kotlin`() {
        val workflow = Files.readString(repository.resolve(".github/workflows/release.yml"))
        val script = Files.readString(repository.resolve("scripts/verify-appimage-runtime-build.sh"))
        val desktop = Files.readString(repository.resolve("modules/desktop/build.gradle.kts"))
        val verifier =
            Files.readString(
                repository.resolve("modules/release-tools/src/main/kotlin/de/heckenmann/visualagent/release/AppImageRuntime.kt"),
            )
        for (text in listOf(
            "tasks.register<Exec>(\"buildAppImageRuntime\")",
            "dependsOn(downloadAppImageTool, buildAppImageRuntime, prepareAppImage)",
            "tasks.register<Exec>(\"verifyAppImageRuntimeInPackage\")",
            "dependsOn(packageAppImage)",
            "verify-appimage-runtime",
        )) {
            assertTrue(desktop.contains(text))
        }
        assertTrue(verifier.contains(".digest_md5"))
        assertTrue(verifier.contains("runtime differs outside appimagetool's MD5 section"))
        for (text in listOf(
            "install -m 755 \"\$runtime\" \"\$output_runtime\"",
            "build-with-docker.sh",
            "readelf -l",
            "--appimage-help",
        )) {
            assertTrue(script.contains(text))
        }
        assertTrue(workflow.contains("task: verifyAppImageRuntimeInPackage"))
    }

    @Test
    fun `platform inventory keeps partial sources distinct from complete release inputs`() {
        val workflow = Files.readString(repository.resolve(".github/workflows/license-inventory.yml"))
        assertTrue(workflow.contains(":desktop:prepareLibrarySourceMaterials"))
        assertFalse(workflow.contains(":desktop:prepareReleaseSourceMaterials"))
        assertTrue(workflow.contains("modules/desktop/build/reports/dependency-license/library-source-materials/"))
        for (platform in listOf(
            "linux-x64",
            "macos-arm64",
            "macos-x64",
            "windows-x64",
        )) {
            assertTrue(workflow.contains("platform: $platform"))
        }
        assertFalse(workflow.contains("continue-on-error:"))
    }
}
