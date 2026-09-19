package de.heckenmann.visualagent.protocol

/** User-selected parameters for an application update operation. */
data class UpdateRequest(
    val includePrerelease: Boolean = false,
    val packageType: String? = null,
    val releaseTag: String? = null,
    val assetName: String? = null,
)

/** Asset metadata exposed to the desktop presentation. */
data class UpdateAsset(
    val name: String,
    val sizeBytes: Long,
    val sha256: String?,
)

/** Current release information shown by the update settings. */
data class UpdateStatus(
    val currentVersion: String,
    val latestVersion: String?,
    val releaseTag: String? = null,
    val updateAvailable: Boolean,
    val channel: String,
    val releaseName: String?,
    val releaseNotes: String?,
    val releaseUrl: String?,
    val selectedAsset: UpdateAsset?,
    val availableAssets: List<UpdateAsset>,
    val error: String? = null,
)

/** Result of a verified download staged outside the database and workspace. */
data class StagedUpdate(
    val stagedId: String,
    val version: String,
    val asset: UpdateAsset,
)

/** Result of staging an application update. */
data class UpdateDownloadResult(
    val staged: StagedUpdate? = null,
    val error: String? = null,
)

/** Result of an explicit installation request. */
data class UpdateInstallResult(
    val started: Boolean,
    val message: String,
)

/** Toolkit-neutral boundary for application update operations. */
interface UpdatePort {
    /** Checks the configured GitHub release channel. */
    fun check(request: UpdateRequest): UpdateStatus

    /** Downloads and verifies the selected asset into a managed staging directory. */
    fun download(request: UpdateRequest): UpdateDownloadResult

    /** Starts installation of a previously verified staged asset. */
    fun install(stagedId: String): UpdateInstallResult
}
