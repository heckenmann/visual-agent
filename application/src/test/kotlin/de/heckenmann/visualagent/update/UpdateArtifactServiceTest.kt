package de.heckenmann.visualagent.update

import de.heckenmann.visualagent.config.AppConfigBean
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies safe, digest-bound update staging behavior. */
class UpdateArtifactServiceTest {
    @Test
    fun `matching bytes and digest produce a staged update`() {
        val bytes = "verified update".toByteArray()
        withService(bytes) { service, root ->
            val asset = asset(bytes)
            val result = service.download("1.2.0", asset)

            assertNotNull(result.staged)
            assertTrue(
                Files.walk(root).use { paths ->
                    paths.anyMatch { Files.isRegularFile(it) && !it.toString().endsWith(".part") }
                },
            )
        }
    }

    @Test
    fun `digest mismatch removes the partial download`() {
        val bytes = "tampered update".toByteArray()
        withService(bytes) { service, root ->
            val wrongDigest = sha256(ByteArray(bytes.size) { 0 })
            val result = service.download("1.2.0", asset(bytes).copy(digest = wrongDigest))

            assertNull(result.staged)
            assertContains(result.error.orEmpty(), "SHA-256")
            assertTrue(
                Files.notExists(root.resolve("updates/1.2.0")) ||
                    Files.list(root.resolve("updates/1.2.0")).use { paths -> !paths.findAny().isPresent },
            )
        }
    }

    @Test
    fun `missing digest is rejected before network access`() {
        val bytes = "unverified update".toByteArray()
        withService(bytes) { service, _ ->
            val result = service.download("1.2.0", asset(bytes).copy(digest = null))

            assertNull(result.staged)
            assertContains(result.error.orEmpty(), "no valid SHA-256")
        }
    }

    @Test
    fun `metadata size mismatch rejects the download`() {
        val bytes = "short update".toByteArray()
        withService(bytes) { service, _ ->
            val result = service.download("1.2.0", asset(bytes).copy(sizeBytes = bytes.size.toLong() + 1))

            assertNull(result.staged)
            assertContains(result.error.orEmpty(), "size")
        }
    }

    @Test
    fun `cancelling a download removes the partial file`() {
        val bytes = "partial update".toByteArray()
        withService(HangingReleaseClient(bytes)) { service, root ->
            StepVerifier
                .create(service.downloadReactive("1.2.0", asset(bytes)))
                .thenCancel()
                .verify(Duration.ofSeconds(2))

            assertTrue(
                Files.notExists(root.resolve("updates/1.2.0")) ||
                    Files.list(root.resolve("updates/1.2.0")).use { paths -> !paths.findAny().isPresent },
            )
        }
    }

    @Test
    fun `modified staged bytes cannot be installed`() {
        val bytes = "verified update".toByteArray()
        var launchCount = 0
        withService(bytes, processLauncher = { launchCount += 1 }) { service, root ->
            val result = service.download("1.2.0", asset(bytes))
            val staged =
                Files.list(root.resolve("updates/1.2.0")).use { paths ->
                    paths.filter { !it.toString().endsWith(".part") }.findFirst().orElseThrow()
                }
            Files.write(staged, "modified update".toByteArray())

            val install = service.install(requireNotNull(result.staged).stagedId)

            assertFalse(install.started)
            assertContains(install.message, "verification")
            assertEquals(0, launchCount)
        }
    }

    @Test
    fun `verified appimage can be launched`() {
        val bytes = "verified update".toByteArray()
        val commands = mutableListOf<List<String>>()
        withService(bytes, processLauncher = { commands += it }) { service, _ ->
            val result = service.download("1.2.0", asset(bytes))

            val install = service.install(requireNotNull(result.staged).stagedId)

            assertTrue(install.started)
            assertEquals(1, commands.size)
            assertTrue(commands.single().last().endsWith(".AppImage"))
        }
    }

    @Test
    fun `unsupported package is left for manual installation`() {
        val bytes = "archive update".toByteArray()
        withService(bytes) { service, _ ->
            val result = service.download("1.2.0", asset(bytes).copy(name = "visual-agent.zip"))

            val install = service.install(requireNotNull(result.staged).stagedId)

            assertFalse(install.started)
            assertContains(install.message, "manually")
        }
    }

    private fun withService(
        bytes: ByteArray,
        processLauncher: (List<String>) -> Unit = {},
        block: (UpdateArtifactService, Path) -> Unit,
    ) {
        withService(FakeReleaseClient(bytes), processLauncher, block)
    }

    private fun withService(
        releaseClient: GitHubReleaseClient,
        processLauncher: (List<String>) -> Unit = {},
        block: (UpdateArtifactService, Path) -> Unit,
    ) {
        val root = Files.createTempDirectory("visual-agent-update-test")
        try {
            val config = AppConfigBean().apply { databasePath = root.resolve("visual-agent.db").toString() }
            val service = UpdateArtifactService(config, releaseClient, processLauncher)
            block(service, root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun asset(bytes: ByteArray): GitHubReleaseAsset =
        GitHubReleaseAsset(
            name = "visual-agent-linux-appimage.AppImage",
            sizeBytes = bytes.size.toLong(),
            digest = sha256(bytes),
            downloadUrl = "https://example.invalid/update",
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private class FakeReleaseClient(
        private val bytes: ByteArray,
    ) : GitHubReleaseClient {
        override fun latestRelease(): Mono<GitHubRelease> = Mono.empty()

        override fun releases(): Mono<List<GitHubRelease>> = Mono.just(emptyList())

        override fun download(asset: GitHubReleaseAsset): Flux<DataBuffer> = Flux.just(DefaultDataBufferFactory().wrap(bytes))
    }

    private class HangingReleaseClient(
        private val bytes: ByteArray,
    ) : GitHubReleaseClient {
        override fun latestRelease(): Mono<GitHubRelease> = Mono.empty()

        override fun releases(): Mono<List<GitHubRelease>> = Mono.just(emptyList())

        override fun download(asset: GitHubReleaseAsset): Flux<DataBuffer> =
            Flux.concat(
                Flux.just(DefaultDataBufferFactory().wrap(bytes)),
                Flux.never(),
            )
    }
}
