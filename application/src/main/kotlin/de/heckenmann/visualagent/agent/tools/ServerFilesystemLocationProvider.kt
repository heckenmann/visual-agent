package de.heckenmann.visualagent.agent.tools

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Selects only storage locations owned by the Visual Agent server. */
@Component
class ServerFilesystemLocationProvider(
    @Qualifier("serverDataRoot") private val serverDataRoot: Path,
    @Qualifier("databasePath") private val databasePath: String,
) : FilesystemLocationProvider {
    override fun locations(): List<FilesystemLocation> =
        buildList {
            add(FilesystemLocation("server-data", serverDataRoot))
            add(FilesystemLocation("workspace", serverDataRoot.resolve(WORKSPACE_DIRECTORY)))
            databaseRoot()?.let { add(FilesystemLocation("database", it)) }
            System
                .getProperty(TEMP_DIRECTORY_PROPERTY)
                ?.takeIf(String::isNotBlank)
                ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
                ?.let { add(FilesystemLocation("temporary", it)) }
        }

    private fun databaseRoot(): Path? {
        if (databasePath.startsWith(IN_MEMORY_DATABASE_PREFIX)) return null
        val databaseFile = databasePath.removePrefix(H2_FILE_PREFIX).substringBefore(';').takeIf(String::isNotBlank) ?: return null
        return runCatching {
            Path
                .of(databaseFile)
                .toAbsolutePath()
                .normalize()
                .parent
        }.getOrNull()
    }

    private companion object {
        const val WORKSPACE_DIRECTORY = "workspace"
        const val TEMP_DIRECTORY_PROPERTY = "java.io.tmpdir"
        const val IN_MEMORY_DATABASE_PREFIX = "jdbc:h2:mem:"
        const val H2_FILE_PREFIX = "jdbc:h2:file:"
    }
}
