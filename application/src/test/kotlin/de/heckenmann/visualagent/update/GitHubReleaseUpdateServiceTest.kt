package de.heckenmann.visualagent.update

import de.heckenmann.visualagent.agent.tools.api.UpdateCheckRequest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubReleaseUpdateServiceTest {
    @Test
    fun `stable check reports a newer release and selected asset`() =
        withPlatform("Linux", "amd64") {
            val client = FakeReleaseClient(stable = release("v1.2.0"))
            val result =
                GitHubReleaseUpdateService(
                    client,
                    ApplicationVersionSource { "1.0.0" },
                ).check(
                    UpdateCheckRequest(packageType = "linux-appimage"),
                )

            assertTrue(result.updateAvailable)
            assertEquals("1.2.0", result.latestVersion)
            assertEquals("v1.2.0", result.releaseTag)
            assertEquals("visual-agent-linux-appimage.AppImage", result.selectedAsset?.name)
            assertEquals("digest", result.selectedAsset?.digest)
        }

    @Test
    fun `download uses the exact release tag selected by the user`() =
        withPlatform("Linux", "amd64") {
            val client =
                FakeReleaseClient(
                    stable = release("v1.2.0"),
                    tagged = release("v1.0.0"),
                )
            val result =
                GitHubReleaseUpdateService(
                    client,
                    ApplicationVersionSource { "1.0.0" },
                ).download(
                    de.heckenmann.visualagent.protocol.UpdateRequest(
                        releaseTag = "v1.0.0",
                        assetName = "visual-agent-linux-appimage.AppImage",
                        packageType = "linux-appimage",
                    ),
                )

            assertEquals("No newer update is available", result.error)
        }

    @Test
    fun `preview check excludes drafts and chooses the highest preview version`() =
        withPlatform("Linux", "amd64") {
            val client =
                FakeReleaseClient(
                    all =
                        listOf(
                            release("v1.1.0-rc.1", prerelease = true),
                            release("v1.3.0-rc.1", prerelease = true, draft = true),
                            release("v1.2.0-rc.2", prerelease = true),
                        ),
                )
            val result =
                GitHubReleaseUpdateService(client, ApplicationVersionSource { "1.0.0" })
                    .check(UpdateCheckRequest(includePrerelease = true))

            assertEquals("1.2.0-rc.2", result.latestVersion)
            assertEquals("preview", result.channel)
        }

    @Test
    fun `stable check reports no update when the release is not newer`() =
        withPlatform("Linux", "amd64") {
            val result =
                GitHubReleaseUpdateService(
                    FakeReleaseClient(stable = release("v1.0.0")),
                    ApplicationVersionSource { "1.0.0" },
                ).check(UpdateCheckRequest())

            assertFalse(result.updateAvailable)
            assertEquals("1.0.0", result.latestVersion)
        }

    @Test
    fun `stable check reports no release when GitHub has no release`() =
        withPlatform("Linux", "amd64") {
            val result =
                GitHubReleaseUpdateService(
                    FakeReleaseClient(),
                    ApplicationVersionSource { "1.0.0" },
                ).check(UpdateCheckRequest())

            assertFalse(result.updateAvailable)
            assertEquals("1.0.0", result.currentVersion)
            assertEquals(null, result.latestVersion)
        }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ): GitHubRelease =
        GitHubRelease(
            tagName = tag,
            name = "Visual Agent $tag",
            body = "Release notes",
            htmlUrl = "https://github.com/heckenmann/visual-agent/releases/$tag",
            publishedAt = "2026-09-19T00:00:00Z",
            createdAt = "2026-09-19T00:00:00Z",
            draft = draft,
            prerelease = prerelease,
            assets = listOf(GitHubReleaseAsset("visual-agent-linux-appimage.AppImage", 12, "sha256:digest")),
        )

    private fun <T> withPlatform(
        osName: String,
        architecture: String,
        block: () -> T,
    ): T {
        val previousOs = System.getProperty("os.name")
        val previousArchitecture = System.getProperty("os.arch")
        System.setProperty("os.name", osName)
        System.setProperty("os.arch", architecture)
        return try {
            block()
        } finally {
            restoreProperty("os.name", previousOs)
            restoreProperty("os.arch", previousArchitecture)
        }
    }

    private fun restoreProperty(
        key: String,
        value: String?,
    ) {
        if (value == null) System.clearProperty(key) else System.setProperty(key, value)
    }

    private class FakeReleaseClient(
        private val stable: GitHubRelease? = null,
        private val all: List<GitHubRelease> = emptyList(),
        private val tagged: GitHubRelease? = null,
    ) : GitHubReleaseClient {
        override fun latestRelease(): Mono<GitHubRelease> = stable?.let { Mono.just(it) } ?: Mono.empty()

        override fun releases(): Mono<List<GitHubRelease>> = Mono.just(all)

        override fun releaseByTag(tagName: String): Mono<GitHubRelease> =
            tagged?.takeIf { it.tagName == tagName }?.let { Mono.just(it) } ?: Mono.empty()
    }
}
