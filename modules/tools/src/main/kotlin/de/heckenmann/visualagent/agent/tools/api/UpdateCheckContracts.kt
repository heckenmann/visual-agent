package de.heckenmann.visualagent.agent.tools.api

/** Request parameters for an application update check. */
data class UpdateCheckRequest(
    val includePrerelease: Boolean = false,
    val packageType: String? = null,
)

/** Public release asset metadata safe to return to the model. */
data class UpdateAssetInfo(
    val name: String,
    val sizeBytes: Long,
    val digest: String?,
)

/** Result of comparing the running application with a GitHub release. */
data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val releaseTag: String? = null,
    val updateAvailable: Boolean,
    val channel: String,
    val releaseName: String?,
    val releaseNotes: String?,
    val releaseUrl: String?,
    val publishedAt: String?,
    val selectedAsset: UpdateAssetInfo?,
    val availableAssets: List<UpdateAssetInfo>,
)

/** Application boundary used by the model-facing update check tool. */
fun interface UpdateCheckPort {
    /** Check the configured release channel for an available application update. */
    fun check(request: UpdateCheckRequest): UpdateCheckResult
}
