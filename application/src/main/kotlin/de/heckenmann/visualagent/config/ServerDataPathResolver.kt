package de.heckenmann.visualagent.config

import net.harawata.appdirs.AppDirs
import net.harawata.appdirs.AppDirsFactory
import org.springframework.core.env.Environment
import java.nio.file.Path

/** Resolves the server-owned data root before the Spring persistence context starts. */
internal object ServerDataPathResolver {
    private const val APPLICATION_NAME = "Visual Agent"
    private const val VENDOR_NAME = "de.heckenmann"
    private const val SERVER_DIRECTORY = "server"
    private const val DATABASE_FILE = "visual-agent.db"

    /** Returns the server database path selected by explicit overrides or the platform default. */
    fun databasePath(environment: Environment): String {
        environment
            .getProperty("visual-agent.db.path")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return validateExplicitDatabasePath(it) }
        return serverDataRoot(environment).resolve(DATABASE_FILE).normalize().toString()
    }

    /**
     * Resolves the stable server-owned data root independently from the database engine.
     *
     * An explicit database file still determines the root for compatibility with existing
     * deployments. New persistence adapters should depend on this root rather than parsing the
     * database connection string.
     */
    fun serverDataRoot(environment: Environment): Path {
        environment
            .getProperty("visual-agent.db.path")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return dataRootForDatabasePath(it) }
        return environment
            .getProperty("visual-agent.server.data-root")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { configuredRoot ->
                Path.of(configuredRoot).also {
                    require(it.isAbsolute) {
                        "visual-agent.server.data-root must be an absolute path"
                    }
                }
            }
            ?: defaultServerDataRoot()
    }

    /** Returns the platform-specific, per-user root for server-owned durable data. */
    fun defaultServerDataRoot(appDirs: AppDirs = AppDirsFactory.getInstance()): Path =
        serverDataRoot(appDirs.getUserDataDir(APPLICATION_NAME, null, VENDOR_NAME))

    /** Adds the server namespace to an already resolved platform user-data directory. */
    internal fun serverDataRoot(userDataDirectory: String): Path =
        Path
            .of(userDataDirectory)
            .resolve(SERVER_DIRECTORY)
            .toAbsolutePath()
            .normalize()

    /** Resolves a server data root from a legacy database path during the transition. */
    internal fun dataRootForDatabasePath(databasePath: String): Path {
        val path = databasePath.removePrefix("jdbc:sqlite:")
        if (path == ":memory:" || path.startsWith("file:")) {
            return Path
                .of(System.getProperty("java.io.tmpdir"))
                .resolve("visual-agent-memory-${ProcessHandle.current().pid()}")
                .toAbsolutePath()
                .normalize()
        }
        val databaseFile = Path.of(path).toAbsolutePath().normalize()
        return requireNotNull(databaseFile.parent) { "The database path must have a parent directory" }
    }

    private fun validateExplicitDatabasePath(path: String): String {
        if (path.startsWith("jdbc:sqlite:")) {
            val sqlitePath = path.removePrefix("jdbc:sqlite:")
            require(
                sqlitePath == ":memory:" || sqlitePath.startsWith("file:") || Path.of(sqlitePath).isAbsolute,
            ) { "visual-agent.db.path must use an absolute path or an explicit SQLite URI" }
            return path
        }
        require(Path.of(path).isAbsolute) { "visual-agent.db.path must be an absolute path" }
        return path
    }
}
