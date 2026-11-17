package de.heckenmann.visualagent.release

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Retains package-inventory filtering and workflow integration contracts after collector migration. */
class NoticeWorkflowContractTest {
    @TempDir
    lateinit var root: Path
    private val repository: Path = Path.of(System.getProperty("release.tools.repository"))

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX inventory helper contract")
    fun `package inventory lists actual notice files without directories or classes`() {
        val fixture = NoticeFixture(root)
        fixture.archive(
            listOf(
                "META-INF/LICENSE.txt",
                "META-INF/COPYRIGHT",
                "META-INF/DISCLAIMER.md",
                "AUTHORS",
                "META-INF/license/",
                "META-INF/license/AuthMethod.class",
            ).map { it to "content".toByteArray() },
        )
        val output = root.resolve("inventory-output")
        runEvidenceCommand(
            listOf(
                "bash",
                repository.resolve("scripts/generate-package-inventory.sh").toString(),
                fixture.packaged.toString(),
                output.toString(),
            ),
            root,
        )
        assertEquals(
            setOf(
                "library.jar: META-INF/LICENSE.txt",
                "library.jar: META-INF/COPYRIGHT",
                "library.jar: META-INF/DISCLAIMER.md",
                "library.jar: AUTHORS",
            ),
            Files.readAllLines(output.resolve("embedded-notices.txt")).toSet(),
        )
    }

    @Test
    fun `platform workflows retain original notices and explicit package evidence inputs`() {
        val inventory = Files.readString(repository.resolve(".github/workflows/license-inventory.yml"))
        val release = Files.readString(repository.resolve(".github/workflows/release.yml"))
        for (workflow in listOf(inventory, release)) {
            assertTrue(workflow.contains("scripts/release-tools.sh collect-notices"))
            assertTrue(workflow.contains("--inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv"))
            assertTrue(workflow.contains("--license-report build/reports/dependency-license/licenses-all.json"))
            assertTrue(workflow.contains("--project-license LICENSE"))
        }
        assertTrue(inventory.contains("build/reports/dependency-license/third-party-notices/"))
        assertTrue(inventory.contains("modules/release-tools/**"))
        assertTrue(release.contains("generateLicenseReport :desktop:generateDistributionArtifactInventory"))
        assertTrue(release.contains("--appimage-package"))
        assertTrue(release.contains("--appimage-runtime modules/desktop/build/appimage/tools/runtime-dd6cebed-x86_64"))
        assertTrue(release.contains("--appimage-source-archive"))
        assertTrue(release.contains("if: matrix.collect_notice_evidence"))
        for (platform in listOf("linux-deb", "linux-rpm", "linux-appimage", "macos-arm64", "macos-x64", "windows")) {
            assertTrue(Regex("platform: $platform\\n(?:.*\\n){1,6}\\s+collect_notice_evidence: true").containsMatchIn(release), platform)
        }
    }
}
