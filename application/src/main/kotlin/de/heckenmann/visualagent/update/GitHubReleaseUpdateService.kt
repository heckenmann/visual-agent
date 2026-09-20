package de.heckenmann.visualagent.update

import de.heckenmann.visualagent.agent.tools.api.UpdateAssetInfo
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckPort
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckRequest
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckResult
import de.heckenmann.visualagent.protocol.UpdateAsset
import de.heckenmann.visualagent.protocol.UpdateDownloadResult
import de.heckenmann.visualagent.protocol.UpdateInstallResult
import de.heckenmann.visualagent.protocol.UpdatePort
import de.heckenmann.visualagent.protocol.UpdateRequest
import de.heckenmann.visualagent.protocol.UpdateStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Compares the running version with the applicable GitHub release. */
@Component
internal class GitHubReleaseUpdateService(
    private val releases: GitHubReleaseClient,
    private val versionSource: ApplicationVersionSource,
    private val artifacts: UpdateArtifactService? = null,
) : UpdateCheckPort,
    UpdatePort {
    /** Synchronous model-tool compatibility boundary; the lookup itself is [checkReactive]. */
    override fun check(request: UpdateCheckRequest): UpdateCheckResult =
        checkReactive(request).block(CHECK_TIMEOUT)
            ?: error("GitHub did not return an update result")

    override fun check(request: UpdateRequest): UpdateStatus =
        check(
            UpdateCheckRequest(
                includePrerelease = request.includePrerelease,
                packageType = request.packageType,
            ),
        ).toProtocol()

    /** Synchronous protocol compatibility boundary; the pipeline is [downloadReactive]. */
    override fun download(request: UpdateRequest): UpdateDownloadResult =
        runCatching {
            downloadReactive(request).block(DOWNLOAD_TIMEOUT)
                ?: UpdateDownloadResult(error = "Update download failed")
        }.getOrElse { error ->
            UpdateDownloadResult(error = error.message ?: "Update download failed")
        }

    /** Downloads a selected release asset without blocking the reactive update pipeline. */
    internal fun downloadReactive(request: UpdateRequest): Mono<UpdateDownloadResult> {
        val packageType = request.packageType ?: InstallationPlatform.defaultPackageType()
        val current = currentVersion()
        return releaseForDownload(request)
            .switchIfEmpty(Mono.error(IllegalStateException("No release is available for the selected channel")))
            .flatMap { release ->
                if (release.draft || release.prerelease != request.includePrerelease) {
                    return@flatMap Mono.just(UpdateDownloadResult(error = "The selected release is no longer available for this channel"))
                }
                val version =
                    requireNotNull(SemanticVersion.parse(release.tagName)) { "GitHub returned an invalid release version" }
                if (version <= current) return@flatMap Mono.just(UpdateDownloadResult(error = "No newer update is available"))
                val selection =
                    ReleaseAssetSelector.select(
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"),
                        packageType,
                        release.assets,
                    )
                val asset =
                    if (request.assetName != null) {
                        selection.matchingAssets.firstOrNull { it.name == request.assetName }
                    } else {
                        selection.selected
                    }
                if (asset == null) {
                    return@flatMap Mono.just(
                        UpdateDownloadResult(
                            error =
                                if (request.assetName != null) {
                                    "The selected release asset is no longer available"
                                } else {
                                    "No unambiguous package asset is available for $packageType"
                                },
                        ),
                    )
                }
                requireNotNull(artifacts) { "Update download service is not configured" }
                    .downloadReactive(version.toString(), asset)
            }.onErrorResume { error -> Mono.just(UpdateDownloadResult(error = error.message ?: "Update download failed")) }
    }

    override fun install(stagedId: String): UpdateInstallResult =
        requireNotNull(artifacts) { "Update download service is not configured" }.install(stagedId)

    private fun checkReactive(request: UpdateCheckRequest): Mono<UpdateCheckResult> {
        val current = currentVersion()
        return findRelease(request.includePrerelease)
            .map { release -> resultFor(release, current, request) }
            .defaultIfEmpty(noReleaseResult(current, request))
    }

    private fun findRelease(includePrerelease: Boolean): Mono<GitHubRelease> =
        if (!includePrerelease) {
            releases.latestRelease().filter { !it.draft && !it.prerelease }
        } else {
            releases
                .releases()
                .flatMapMany { candidates -> Flux.fromIterable(candidates) }
                .filter { !it.draft && it.prerelease }
                .collectList()
                .flatMap { candidates ->
                    candidates
                        .mapNotNull { release -> SemanticVersion.parse(release.tagName)?.let { it to release } }
                        .maxWithOrNull(compareBy({ it.first }, { it.second.createdAt.orEmpty() }))
                        ?.second
                        ?.let { release -> Mono.just(release) }
                        ?: Mono.empty()
                }
        }

    private fun releaseForDownload(request: UpdateRequest): Mono<GitHubRelease> =
        request.releaseTag?.let(releases::releaseByTag) ?: findRelease(request.includePrerelease)

    private fun resultFor(
        release: GitHubRelease,
        current: SemanticVersion,
        request: UpdateCheckRequest,
    ): UpdateCheckResult {
        val releaseVersion = requireNotNull(SemanticVersion.parse(release.tagName)) { "GitHub returned an invalid release version" }
        val selection =
            ReleaseAssetSelector.select(
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                request.packageType,
                release.assets,
            )
        return UpdateCheckResult(
            currentVersion = current.toString(),
            latestVersion = releaseVersion.toString(),
            releaseTag = release.tagName,
            updateAvailable = releaseVersion > current,
            channel = channel(request.includePrerelease),
            releaseName = release.name,
            releaseNotes = release.body?.take(MAX_RELEASE_NOTES_LENGTH),
            releaseUrl = release.htmlUrl,
            publishedAt = release.publishedAt,
            selectedAsset = selection.selected?.let(::toUpdateAssetInfo),
            availableAssets = selection.matchingAssets.map(::toUpdateAssetInfo),
        )
    }

    private fun noReleaseResult(
        current: SemanticVersion,
        request: UpdateCheckRequest,
    ): UpdateCheckResult =
        UpdateCheckResult(
            currentVersion = current.toString(),
            latestVersion = null,
            releaseTag = null,
            updateAvailable = false,
            channel = channel(request.includePrerelease),
            releaseName = null,
            releaseNotes = null,
            releaseUrl = null,
            publishedAt = null,
            selectedAsset = null,
            availableAssets = emptyList(),
        )

    private fun channel(includePrerelease: Boolean): String = if (includePrerelease) "preview" else "stable"

    private fun currentVersion(): SemanticVersion {
        val currentText = versionSource.currentVersion().trim().removePrefix("v")
        return requireNotNull(SemanticVersion.parse(currentText)) { "The current application version is invalid" }
    }

    private fun toUpdateAssetInfo(asset: GitHubReleaseAsset) =
        UpdateAssetInfo(
            name = asset.name,
            sizeBytes = asset.sizeBytes,
            digest = asset.digest?.removePrefix("sha256:"),
        )

    private companion object {
        val CHECK_TIMEOUT = Duration.ofSeconds(20)
        val DOWNLOAD_TIMEOUT = Duration.ofMinutes(10)
        const val MAX_RELEASE_NOTES_LENGTH = 4_000
    }
}

private object InstallationPlatform {
    fun defaultPackageType(): String? =
        System.getProperty("visual-agent.package.type")?.trim()?.takeIf(String::isNotEmpty)
            ?: when {
                System.getProperty("os.name").contains("win", ignoreCase = true) ->
                    if (runningFromJar()) "windows-jar" else "windows-msi"
                System.getProperty("os.name").contains("mac", ignoreCase = true) ->
                    if (runningFromJar()) "macos-jar" else "macos-dmg"
                System.getProperty("os.name").contains("linux", ignoreCase = true) -> linuxPackageType()
                else -> null
            }

    private fun linuxPackageType(): String? =
        when {
            !System.getenv("APPIMAGE").isNullOrBlank() -> "linux-appimage"
            packageManagerReportsInstalled("dpkg-query", "-W", "-f=\${Status}", "visual-agent") -> "linux-deb"
            packageManagerReportsInstalled("rpm", "-q", "visual-agent") -> "linux-rpm"
            runningFromJar() -> "linux-jar"
            else -> null
        }

    private fun packageManagerReportsInstalled(vararg command: String): Boolean =
        runCatching {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            process.waitFor(PACKAGE_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)

    private fun runningFromJar(): Boolean {
        val classPath = System.getProperty("java.class.path")
        return classPath
            .split(File.pathSeparator)
            .any { Files.isRegularFile(Path.of(it)) && it.endsWith(".jar", ignoreCase = true) }
    }

    private const val PACKAGE_QUERY_TIMEOUT_SECONDS = 2L
}

private fun UpdateCheckResult.toProtocol(): UpdateStatus =
    UpdateStatus(
        currentVersion = currentVersion,
        latestVersion = latestVersion,
        releaseTag = releaseTag,
        updateAvailable = updateAvailable,
        channel = channel,
        releaseName = releaseName,
        releaseNotes = releaseNotes,
        releaseUrl = releaseUrl,
        selectedAsset =
            selectedAsset?.let {
                UpdateAsset(it.name, it.sizeBytes, it.digest)
            },
        availableAssets =
            availableAssets.map { asset ->
                UpdateAsset(asset.name, asset.sizeBytes, asset.digest)
            },
    )
