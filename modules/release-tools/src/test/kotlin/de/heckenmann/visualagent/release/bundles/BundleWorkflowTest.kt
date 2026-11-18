package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.runEvidenceCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Preserves release-archive workflow wiring and Windows-style checkout license bytes. */
class BundleWorkflowTest {
    @TempDir
    lateinit var root: Path
    private val repository: Path = Path.of(System.getProperty("release.tools.repository"))

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX Git fixture invocation")
    fun `project license bytes survive Windows style checkout`() {
        val checkout = Files.createDirectory(root.resolve("checkout"))
        val attributes = Files.write(root.resolve("empty-global-attributes"), byteArrayOf())
        val original = "Project license\nCopyright notice\n".toByteArray()
        Files.copy(repository.resolve(".gitattributes"), checkout.resolve(".gitattributes"))
        Files.write(checkout.resolve("LICENSE"), original)
        Files.writeString(checkout.resolve("ordinary.txt"), "Ordinary text\n")
        val git = listOf("git", "-C", checkout.toString(), "-c", "core.attributesFile=$attributes", "-c", "core.safecrlf=false")
        for (args in listOf(
            listOf("init", "--quiet"),
            listOf("config", "core.autocrlf", "true"),
            listOf("config", "core.eol", "crlf"),
            listOf("add", ".gitattributes", "LICENSE", "ordinary.txt"),
        )) {
            runEvidenceCommand(git + args, root)
        }
        Files.delete(checkout.resolve("LICENSE"))
        Files.delete(checkout.resolve("ordinary.txt"))
        runEvidenceCommand(git + listOf("checkout-index", "--all"), root)
        assertContentEquals("Ordinary text\r\n".toByteArray(), Files.readAllBytes(checkout.resolve("ordinary.txt")))
        assertContentEquals(original, Files.readAllBytes(checkout.resolve("LICENSE")))
    }

    @Test
    fun `release workflow builds and uploads both review and recipient archive pairs`() {
        val workflow = Files.readString(repository.resolve(".github/workflows/release.yml"))
        for (required in listOf(
            "prepareReleaseSourceMaterials",
            ":desktop:generateDistributionArtifactInventory",
            ":release-tools:installDist",
            "scripts/release-tools.sh collect-notices",
            "--package-root modules/desktop/build/compose/binaries/main/app",
            "--inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv",
            "--output-directory release-materials/review-only",
            "--output-directory release-materials/recipient-candidates",
            "--recipient-candidate",
            "needs: [verify, package]",
            "pattern: license-notices-*",
            "if: matrix.collect_notice_evidence",
            "scripts/release-tools.sh build-bundles",
            "name: visual-agent-legal-review",
            "name: visual-agent-corresponding-sources-review",
            "name: visual-agent-legal\n",
            "name: visual-agent-corresponding-sources\n",
        )) {
            assertTrue(workflow.contains(required), required)
        }
        assertEquals(
            2,
            Regex(
                Regex.escape("--source-materials modules/desktop/build/reports/dependency-license/release-source-materials"),
            ).findAll(workflow).count(),
        )
        assertEquals(2, Regex(Regex.escape("--notice-evidence release-materials/package-notices")).findAll(workflow).count())
        for (platform in bundleNoticePlatforms) {
            assertTrue(workflow.contains("license-notices-\${{ matrix.platform }}"))
            assertTrue(Regex("platform: $platform\\n(?:.*\\n){1,6}\\s+collect_notice_evidence: true").containsMatchIn(workflow))
        }
    }
}
