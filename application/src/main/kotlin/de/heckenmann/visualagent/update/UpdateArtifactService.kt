package de.heckenmann.visualagent.update

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.StagedUpdate
import de.heckenmann.visualagent.protocol.UpdateAsset
import de.heckenmann.visualagent.protocol.UpdateDownloadResult
import de.heckenmann.visualagent.protocol.UpdateInstallResult
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Downloads verified release assets outside the database and workspace. */
@Component
internal class UpdateArtifactService(
    private val config: AppConfigBean,
    private val releases: GitHubReleaseClient,
    private val processLauncher: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
) {
    private val stagedFiles = ConcurrentHashMap<String, StagedArtifact>()

    /** Downloads one asset, verifies its GitHub SHA-256 digest, and returns a staging handle. */
    fun download(
        version: String,
        asset: GitHubReleaseAsset,
    ): UpdateDownloadResult {
        val expectedDigest =
            asset.digest
                ?.removePrefix("sha256:")
                ?.lowercase()
                ?.takeIf { it.matches(SHA256_PATTERN) }
                ?: return UpdateDownloadResult(error = "The selected release asset has no valid SHA-256 digest")
        if (asset.sizeBytes !in 1..MAX_ASSET_SIZE) {
            return UpdateDownloadResult(error = "The selected release asset exceeds the supported size limit")
        }
        val id = UUID.randomUUID().toString()
        val directory = stagingRoot().resolve(version).normalize()
        val partial = directory.resolve("$id.part")
        val verified = directory.resolve("$id.${asset.name.substringAfterLast('.', "bin")}")
        return runCatching {
            Files.createDirectories(directory)
            val downloadedBytes = AtomicLong()
            val stream =
                releases
                    .download(asset)
                    .doOnNext { buffer ->
                        if (downloadedBytes.addAndGet(buffer.readableByteCount().toLong()) > MAX_ASSET_SIZE) {
                            throw IllegalStateException("The downloaded release asset exceeds the supported size limit")
                        }
                    }
            DataBufferUtils.write(stream, partial).block(DOWNLOAD_TIMEOUT)
            require(Files.size(partial) == asset.sizeBytes) { "The downloaded release asset size does not match GitHub metadata" }
            require(sha256(partial) == expectedDigest) { "The downloaded release asset failed SHA-256 verification" }
            Files.move(partial, verified)
            stagedFiles[id] = StagedArtifact(verified, UpdateAsset(asset.name, asset.sizeBytes, expectedDigest))
            UpdateDownloadResult(
                staged = StagedUpdate(id, version, UpdateAsset(asset.name, asset.sizeBytes, expectedDigest)),
            )
        }.getOrElse { error ->
            Files.deleteIfExists(partial)
            Files.deleteIfExists(verified)
            UpdateDownloadResult(error = error.message ?: "Update download failed")
        }
    }

    /** Starts the platform helper for a verified package after explicit user confirmation. */
    fun install(stagedId: String): UpdateInstallResult {
        val artifact =
            stagedFiles[stagedId]
                ?: return UpdateInstallResult(false, "The staged update is no longer available")
        val file = artifact.path
        val stillVerified =
            runCatching {
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                    Files.size(file) == artifact.asset.sizeBytes &&
                    sha256(file) == artifact.asset.sha256
            }.getOrDefault(false)
        if (!stillVerified) {
            stagedFiles.remove(stagedId)
            return UpdateInstallResult(false, "The staged update failed verification before installation")
        }
        val command =
            installationCommand(file)
                ?: return UpdateInstallResult(
                    false,
                    "This package must be installed manually: ${file.fileName}",
                )
        return runCatching {
            processLauncher(command)
            stagedFiles.remove(stagedId)
            UpdateInstallResult(
                true,
                "The verified update installer was started. Complete the installation and restart Visual Agent when prompted.",
            )
        }.getOrElse { error -> UpdateInstallResult(false, error.message ?: "Could not start the update installer") }
    }

    private fun stagingRoot(): Path {
        val database = config.databasePath.removePrefix("jdbc:sqlite:")
        val databasePath = Path.of(database).toAbsolutePath().normalize()
        return databasePath.parent.resolve("updates").normalize()
    }

    private fun installationCommand(file: Path): List<String>? =
        when {
            file.fileName.toString().endsWith(".msi", ignoreCase = true) -> listOf("msiexec", "/i", file.toString(), "/passive")
            file.fileName.toString().endsWith(".dmg", ignoreCase = true) -> listOf("open", file.toString())
            file.fileName.toString().endsWith(".deb", ignoreCase = true) ||
                file.fileName.toString().endsWith(".rpm", ignoreCase = true) ||
                file.fileName.toString().endsWith(".AppImage", ignoreCase = true) -> listOf("xdg-open", file.toString())
            else -> null
        }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class StagedArtifact(
        val path: Path,
        val asset: UpdateAsset,
    )

    private companion object {
        const val MAX_ASSET_SIZE = 1_073_741_824L
        val DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(10)
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
