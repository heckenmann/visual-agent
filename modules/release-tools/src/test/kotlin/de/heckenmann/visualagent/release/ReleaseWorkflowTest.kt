package de.heckenmann.visualagent.release

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Migrated release guard and draft-attachment behavior using the actual shell scripts. */
@DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX release publisher runs on Ubuntu")
class ReleaseWorkflowTest {
    @TempDir
    lateinit var root: Path
    private lateinit var fixture: ReleaseWorkflowFixture

    @BeforeEach
    fun prepare() {
        fixture = ReleaseWorkflowFixture(root)
    }

    @Test
    fun `semver and ancestry guards preserve accepted and rejected inputs`() {
        for (tag in listOf("v1.2.3", "v1.2.3-beta.1+build.7")) {
            assertEquals(0, fixture.run("verify-release-ref.sh", "RELEASE_TAG" to tag).first)
        }
        for (tag in listOf("1.2.3", "v01.2.3", "v1.2.3-01", "v1.2.3/unsafe", "v1.2.3+")) {
            assertTrue(fixture.run("verify-release-ref.sh", "RELEASE_TAG" to tag).first != 0)
        }
        for (relation in listOf("behind", "diverged")) {
            assertTrue(fixture.run("verify-release-ref.sh", "RELATION" to relation).first != 0)
        }
        assertEquals(0, fixture.run("verify-release-ref.sh", "RELATION" to "identical").first)
    }

    @Test
    fun `moved tag prevents creation and upload`() {
        assertTrue(fixture.run(overrides = arrayOf("TAG_COMMIT" to "b".repeat(40))).first != 0)
        assertFalse(fixture.log().contains("release create"))
        assertFalse(fixture.log().contains("release upload"))
    }

    @Test
    fun `original event must create the exact tag without force or deletion`() {
        val valid = """{"ref":"refs/tags/v1.2.3","created":true,"forced":false,"deleted":false}"""
        val invalid =
            listOf(
                valid.replace("\"created\":true", "\"created\":false").replace("\"forced\":false", "\"forced\":true"),
                valid.replace("\"created\":true", "\"created\":false"),
                valid.replace("\"forced\":false", "\"forced\":true"),
                valid.replace("\"deleted\":false", "\"deleted\":true"),
                valid.replace("refs/tags/", "refs/heads/"),
                """{"ref":"refs/tags/v1.2.3"}""",
            )
        for (payload in invalid) {
            val event = Files.writeString(root.resolve("invalid.json"), payload)
            val result = fixture.run(overrides = arrayOf("GITHUB_EVENT_PATH" to event.toString(), "EXISTING" to "draft"))
            assertTrue(result.first != 0)
            assertTrue(result.second.contains("initial tag creation"))
        }
        assertEquals("", fixture.log())
    }

    @Test
    fun `missing and malformed events fail closed`() {
        val event = root.resolve("missing.json")
        assertTrue(fixture.run("verify-release-ref.sh", "GITHUB_EVENT_PATH" to event.toString()).first != 0)
        Files.writeString(event, "not JSON")
        assertTrue(fixture.run("verify-release-ref.sh", "GITHUB_EVENT_PATH" to event.toString()).first != 0)
        assertEquals("", fixture.log())
    }

    @Test
    fun `missing asset stops before GitHub operations`() {
        Files.delete(fixture.assets.resolve("visual-agent-windows.msi"))
        assertTrue(fixture.run().first != 0)
        assertEquals("", fixture.log())
    }

    @Test
    fun `new draft contains twelve files and retry reuses it`() {
        for (attempt in listOf("1", "2")) {
            val result = fixture.run(overrides = arrayOf("GITHUB_RUN_ATTEMPT" to attempt))
            assertEquals(0, result.first, result.second)
        }
        val log = fixture.log()
        assertEquals(1, Regex("release create").findAll(log).count())
        assertEquals(2, Regex("release upload").findAll(log).count())
        ReleaseAssets.requiredNames.forEach { assertTrue(log.contains(fixture.assets.resolve(it).toString())) }
        assertTrue(log.contains("--draft --prerelease=false"))
        assertTrue(log.contains("--title v1.2.3 --generate-notes"))
        assertFalse(log.contains("Visual Agent v1.2.3"))
    }

    @Test
    fun `existing draft preserves notes and unrelated assets`() {
        val result = fixture.run(overrides = arrayOf("EXISTING" to "draft"))
        assertEquals(0, result.first, result.second)
        for (forbidden in listOf("release create", "release edit", "PATCH", "extra.txt")) {
            assertFalse(fixture.log().contains(forbidden))
        }
    }

    @Test
    fun `existing draft title changes without replacing notes`() {
        val result = fixture.run(overrides = arrayOf("EXISTING" to "draft", "RELEASE_TITLE" to "Visual Agent v1.2.3"))
        assertEquals(0, result.first, result.second)
        assertTrue(fixture.log().contains("api --method PATCH repos/owner/repo/releases/7 -f name=v1.2.3 --silent"))
        assertFalse(fixture.log().contains("body="))
        assertFalse(fixture.log().contains("release create"))
    }

    @Test
    fun `published release and API failures prevent writes`() {
        for (existing in listOf("published", "error")) {
            assertTrue(fixture.run(overrides = arrayOf("EXISTING" to existing)).first != 0)
        }
        assertFalse(fixture.log().contains("release upload"))
        assertFalse(fixture.log().contains("release create"))
    }

    @Test
    fun `published release recovery requires a numeric source run before GitHub access`() {
        val result = fixture.run("attach-published-release-assets.sh", "SOURCE_RUN_ID" to "44947970996;echo unsafe")

        assertTrue(result.first != 0)
        assertTrue(result.second.contains("must be numeric"))
        assertEquals("", fixture.log())
    }

    @Test
    fun `published release recovery uploads only verified assets from the failed tag run`() {
        fixture.preparePublishedRecovery()
        val result =
            fixture.run(
                "attach-published-release-assets.sh",
                "EXISTING" to "published",
                "INITIAL_ASSETS" to """[[{"id":41,"name":"visual-agent-windows.msi","state":"starter","size":0}]]""",
            )

        assertEquals(0, result.first, result.second)
        assertTrue(result.second.contains("Published release v1.2.3 has all twelve verified assets."))
        assertEquals(13, Regex("actions/artifacts/\\d+/zip").findAll(fixture.log()).count())
        assertTrue(fixture.log().contains("api -X DELETE repos/owner/repo/releases/assets/41"))
        assertTrue(fixture.log().contains("release upload v1.2.3"))
        assertFalse(fixture.log().contains("--clobber"))
        assertFalse(fixture.log().contains("release edit"))
        assertFalse(fixture.log().contains("PATCH"))
    }

    @Test
    fun `published release recovery stops when the source run restarts`() {
        fixture.preparePublishedRecovery()
        val result = fixture.run("attach-published-release-assets.sh", "CURRENT_ATTEMPT" to "2")

        assertTrue(result.second.contains("Source run was restarted"))
        assertFalse(fixture.log().contains("actions/artifacts/"))
        assertFalse(fixture.log().contains("release upload"))
    }

    @Test
    fun `published release recovery is an Actions workflow that consumes a source run ID`() {
        val workflow = Files.readString(fixture.repository.resolve(".github/workflows/recover-published-release-assets.yml"))

        assertTrue(workflow.contains("workflow_dispatch:"))
        assertTrue(workflow.contains("source_run_id:"))
        assertTrue(workflow.contains("contents: write"))
        assertTrue(workflow.contains("scripts/attach-published-release-assets.sh"))
        assertFalse(workflow.contains("gh release upload"))
    }

    @Test
    fun `prerelease and build metadata classification is preserved`() {
        val preview = fixture.run(overrides = arrayOf("RELEASE_TAG" to "v1.2.3-beta.1+build.7", "PREVIEW" to "true"))
        assertEquals(0, preview.first, preview.second)
        assertTrue(fixture.log().contains("--prerelease=true"))
        val metadata = fixture.run(overrides = arrayOf("RELEASE_TAG" to "v1.2.3+build.7"))
        assertEquals(0, metadata.first, metadata.second)
        assertTrue(fixture.run(overrides = arrayOf("EXISTING" to "draft", "PREVIEW" to "true")).first != 0)
    }

    @Test
    fun `workflow waits for all jobs and consumes only same run artifacts`() {
        val workflow = Files.readString(fixture.repository.resolve(".github/workflows/release.yml"))
        val pattern = Regex("^  ([a-z-]+):\\n(.*?)(?=^  [a-z-]+:|\\z)", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val jobs = pattern.findAll(workflow.substringAfter("\njobs:\n")).associate { it.groupValues[1] to it.groupValues[2] }
        val draft = jobs.getValue("draft")
        val needs =
            Regex("needs: \\[(.*?)]")
                .find(draft)!!
                .groupValues[1]
                .split(", ")
                .toSet()
        assertEquals(jobs.keys - "draft", needs)
        assertFalse(draft.contains("always()"))
        assertTrue(draft.contains("if: github.event_name == 'push'"))
        assertTrue(draft.contains("gh run download \"\$GITHUB_RUN_ID\""))
        for (required in listOf(
            "ref: \${{ github.sha }}",
            "push:\n    tags:\n      - 'v*'",
            "  workflow_dispatch:\n",
            "cancel-in-progress: false",
            "group: desktop-release-\${{ github.ref }}",
        )) {
            assertTrue(workflow.contains(required), required)
        }
        for (removed in listOf("workflow_run", "publish_release", "release-candidate", "inputs.release_tag")) {
            assertFalse(workflow.contains(removed))
        }
        assertFalse(workflow.substringBefore("\njobs:").contains("contents: write"))
        assertEquals(1, Regex("contents: write").findAll(workflow).count())
        assertEquals(Regex("uses: actions/upload-artifact@").findAll(workflow).count(), Regex("overwrite: true").findAll(workflow).count())
        assertEquals(8, Regex(Regex.escape("ref: \${{ github.sha }}")).findAll(workflow).count())
        assertFalse(Files.exists(fixture.repository.resolve(".github/workflows/package-smoke.yml")))
    }
}
