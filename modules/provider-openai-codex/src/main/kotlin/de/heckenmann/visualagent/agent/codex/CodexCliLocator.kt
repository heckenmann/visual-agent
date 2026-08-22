package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Locates a user-selected or user-local Codex CLI executable without scanning arbitrary directories.
 */
@Component
internal class CodexCliLocator(
    private val environment: CodexCliEnvironment,
    private val versionProbe: CodexCliVersionProbe,
) {
    /**
     * Resolves the Codex executable using the configured path before automatic candidates.
     *
     * An invalid configured path is terminal: automatic discovery is intentionally not attempted.
     *
     * @param configuredPath Optional absolute executable selected by the user
     * @return A ready executable, a missing result, or the invalid explicit-path result
     */
    suspend fun locate(configuredPath: String?): CodexCliLocation =
        withContext(Dispatchers.IO) {
            val explicitPath = configuredPath?.trim()?.takeIf(String::isNotBlank)
            if (explicitPath == null) automaticCandidate() else explicitCandidate(explicitPath)
        }

    private suspend fun explicitCandidate(configuredPath: String): CodexCliLocation {
        val executable = runCatching { Path.of(configuredPath).normalize() }.getOrNull()
        if (executable == null || !executable.isAbsolute || !isExecutable(executable)) {
            return CodexCliLocation.InvalidExplicitPath
        }
        return versionProbe.probe(executable)?.let { version ->
            CodexCliLocation.Ready(executable, version, CodexCliLocationSource.EXPLICIT)
        } ?: CodexCliLocation.InvalidExplicitPath
    }

    private suspend fun automaticCandidate(): CodexCliLocation {
        for ((candidate, source) in automaticCandidates()) {
            if (!isExecutable(candidate)) continue
            val version = versionProbe.probe(candidate) ?: continue
            return CodexCliLocation.Ready(candidate, version, source)
        }
        return CodexCliLocation.Missing
    }

    private fun automaticCandidates(): List<Pair<Path, CodexCliLocationSource>> {
        val pathCandidates = environment.pathDirectories().map { it.resolve(executableName()) }
        val home = environment.homeDirectory()
        val userLocalCandidates =
            listOf(
                home.resolve(".local/bin").resolve(executableName()),
                home.resolve("bin").resolve(executableName()),
            )
        return (
            pathCandidates.map { it to CodexCliLocationSource.PATH } +
                userLocalCandidates.map { it to CodexCliLocationSource.USER_LOCAL }
        ).distinctBy { (candidate, _) -> candidate.toAbsolutePath().normalize() }
    }

    private fun executableName(): String = if (environment.isWindows()) "codex.exe" else "codex"

    private fun isExecutable(candidate: Path): Boolean = Files.isRegularFile(candidate) && Files.isExecutable(candidate)
}

/**
 * Supplies the minimal process environment needed for deterministic Codex CLI discovery.
 */
internal interface CodexCliEnvironment {
    /**
     * Returns the inherited PATH entries in their lookup order.
     *
     * @return Existing directory entries, excluding blank values
     */
    fun pathDirectories(): List<Path>

    /**
     * Returns the current user's home directory.
     *
     * @return User home directory
     */
    fun homeDirectory(): Path

    /**
     * Reports whether executable names require a Windows suffix.
     *
     * @return True when running on Windows
     */
    fun isWindows(): Boolean
}

/**
 * Reads the process environment used by the desktop application.
 */
@Component
internal class SystemCodexCliEnvironment : CodexCliEnvironment {
    override fun pathDirectories(): List<Path> =
        System
            .getenv("PATH")
            .orEmpty()
            .split(java.io.File.pathSeparator)
            .filter(String::isNotBlank)
            .mapNotNull { entry -> runCatching { Path.of(entry) }.getOrNull() }

    override fun homeDirectory(): Path = Path.of(System.getProperty("user.home"))

    override fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
}

/**
 * Validates a candidate executable through its machine-readable version command.
 */
internal interface CodexCliVersionProbe {
    /**
     * Executes the bounded `--version` probe.
     *
     * @param executable Candidate executable
     * @return Normalized version text when validation succeeds, otherwise null
     */
    suspend fun probe(executable: Path): String?
}

/**
 * Result of resolving the local Codex CLI.
 */
internal sealed interface CodexCliLocation {
    /**
     * A validated executable and its reported version.
     *
     * @property executable Absolute executable path
     * @property version Version returned by `codex --version`
     * @property source Discovery source
     */
    data class Ready(
        val executable: Path,
        val version: String,
        val source: CodexCliLocationSource,
    ) : CodexCliLocation

    /**
     * No validated automatic candidate was found.
     */
    data object Missing : CodexCliLocation

    /**
     * The explicitly configured path could not be used and must be corrected by the user.
     */
    data object InvalidExplicitPath : CodexCliLocation
}

/**
 * Origin of a successfully validated Codex executable.
 */
internal enum class CodexCliLocationSource {
    EXPLICIT,
    PATH,
    USER_LOCAL,
}
