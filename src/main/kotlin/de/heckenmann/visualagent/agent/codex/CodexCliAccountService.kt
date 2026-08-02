package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Login state reported only by the official Codex CLI. */
enum class CodexLoginStatus {
    SIGNED_IN,
    SIGNED_OUT,
    CLI_MISSING,
    ERROR,
}

/** Result of an explicitly confirmed Codex CLI account action. */
data class CodexAccountActionResult(
    val successful: Boolean,
    val message: String,
)

/** Comparison state between the installed and latest official Codex CLI versions. */
enum class CodexCliUpdateStatus {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    LATEST_UNAVAILABLE,
    CLI_NOT_FOUND,
}

/** Installed and latest official Codex CLI versions shown in provider settings. */
data class CodexCliVersionInfo(
    val installedVersion: String?,
    val latestVersion: String?,
    val status: CodexCliUpdateStatus,
)

/** Executes account operations exclusively through the official Codex CLI. */
@Component
class CodexCliAccountService internal constructor(
    private val locator: CodexCliLocator,
    private val processFactory: CodexCliProcessFactory,
    private val releaseVersionSource: CodexCliReleaseVersionSource,
) {
    /** Queries `codex login status` without reading Codex state files. */
    suspend fun status(explicitPath: String?): CodexLoginStatus =
        withContext(Dispatchers.IO) {
            val executable = resolve(explicitPath) ?: return@withContext CodexLoginStatus.CLI_MISSING
            runCatching {
                processFactory
                    .run(
                        listOf(executable.toString(), "login", "status"),
                        timeoutSeconds = STATUS_TIMEOUT_SECONDS,
                    ).let { result ->
                        when (result.exitCode) {
                            0 -> CodexLoginStatus.SIGNED_IN
                            else -> CodexLoginStatus.SIGNED_OUT
                        }
                    }
            }.getOrDefault(CodexLoginStatus.ERROR)
        }

    /** Resolves the installed CLI version and compares it with the latest official release. */
    suspend fun versionInfo(explicitPath: String?): CodexCliVersionInfo =
        withContext(Dispatchers.IO) {
            val installed =
                when (val location = locator.locate(explicitPath)) {
                    is CodexCliLocation.Ready -> normalizedVersion(location.version)
                    CodexCliLocation.InvalidExplicitPath, CodexCliLocation.Missing -> null
                }
            val latest = releaseVersionSource.latestVersionTag()?.let(::normalizedVersion)
            CodexCliVersionInfo(
                installedVersion = installed,
                latestVersion = latest,
                status = versionStatus(installed, latest),
            )
        }

    /** Starts the official browser-based login after UI confirmation. */
    suspend fun login(explicitPath: String?): CodexAccountActionResult = accountAction(explicitPath, listOf("login"), LOGIN_TIMEOUT_SECONDS)

    /** Starts the official device-code login after UI confirmation. */
    suspend fun deviceLogin(explicitPath: String?): CodexAccountActionResult =
        accountAction(explicitPath, listOf("login", "--device-auth"), LOGIN_TIMEOUT_SECONDS)

    /** Logs out through the official CLI after UI confirmation. */
    suspend fun logout(explicitPath: String?): CodexAccountActionResult =
        accountAction(explicitPath, listOf("logout"), STATUS_TIMEOUT_SECONDS)

    private suspend fun accountAction(
        explicitPath: String?,
        arguments: List<String>,
        timeoutSeconds: Long,
    ): CodexAccountActionResult =
        withContext(Dispatchers.IO) {
            val executable =
                resolve(explicitPath)
                    ?: return@withContext CodexAccountActionResult(false, "Codex CLI is not installed")
            runCatching {
                processFactory
                    .run(
                        listOf(executable.toString()) + arguments,
                        timeoutSeconds = timeoutSeconds,
                    ).let { result ->
                        CodexAccountActionResult(
                            successful = result.exitCode == 0,
                            message =
                                result.stdout.text.ifBlank { result.stderr.text }.ifBlank {
                                    if (result.exitCode == 0) "Codex CLI action completed" else "Codex CLI action failed"
                                },
                        )
                    }
            }.getOrElse { CodexAccountActionResult(false, it.message ?: "Codex CLI action failed") }
        }

    private suspend fun resolve(explicitPath: String?): Path? =
        when (val result = locator.locate(explicitPath)) {
            is CodexCliLocation.Ready -> result.executable
            CodexCliLocation.InvalidExplicitPath, CodexCliLocation.Missing -> null
        }

    private fun versionStatus(
        installed: String?,
        latest: String?,
    ): CodexCliUpdateStatus =
        when {
            installed == null -> CodexCliUpdateStatus.CLI_NOT_FOUND
            latest == null -> CodexCliUpdateStatus.LATEST_UNAVAILABLE
            compareVersions(installed, latest) < 0 -> CodexCliUpdateStatus.UPDATE_AVAILABLE
            else -> CodexCliUpdateStatus.UP_TO_DATE
        }

    private fun normalizedVersion(raw: String): String? = VERSION_PATTERN.find(raw)?.value

    private fun compareVersions(
        installed: String,
        latest: String,
    ): Int {
        val installedParts = installed.split('.').map(String::toInt)
        val latestParts = latest.split('.').map(String::toInt)
        return (0 until maxOf(installedParts.size, latestParts.size))
            .firstNotNullOfOrNull { index ->
                val comparison = (installedParts.getOrNull(index) ?: 0).compareTo(latestParts.getOrNull(index) ?: 0)
                comparison.takeIf { it != 0 }
            } ?: 0
    }

    private companion object {
        private val VERSION_PATTERN = Regex("\\d+(?:\\.\\d+)+")
        private const val STATUS_TIMEOUT_SECONDS = 15L
        private const val LOGIN_TIMEOUT_SECONDS = 600L
    }
}
