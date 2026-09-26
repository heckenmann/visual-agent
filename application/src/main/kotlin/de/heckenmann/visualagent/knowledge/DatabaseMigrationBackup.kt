package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Component
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.Properties
import java.util.UUID
import javax.sql.DataSource

/** Captures the file database and managed workspace while startup is still quiescent. */
@Component
internal class DatabaseMigrationBackup(
    private val dataSource: DataSource,
    private val databasePath: String,
    private val serverDataRoot: Path,
) {
    /** Returns a complete pre-upgrade snapshot, or null for a new or in-memory database. */
    fun createIfNeeded(
        sourceVersion: String?,
        targetVersion: String,
    ): Path? {
        if (databasePath.startsWith("jdbc:h2:mem:") || !hasUserTables()) return null

        val backupRoot = serverDataRoot.resolve("migration-backups")
        require(!Files.isSymbolicLink(backupRoot)) { "The migration backup directory cannot be a symbolic link" }
        Files.createDirectories(backupRoot)
        restrictPermissions(backupRoot, directory = true)
        val staging = Files.createTempDirectory(backupRoot, ".pending-")
        restrictPermissions(staging, directory = true)
        try {
            val databaseArchive = staging.resolve("database.zip")
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("BACKUP TO '${databaseArchive.toAbsolutePath().toString().replace("'", "''")}'")
                }
            }
            require(Files.size(databaseArchive) > 0) { "The H2 backup archive is empty" }
            restrictPermissions(databaseArchive, directory = false)
            copyWorkspace(staging.resolve("workspace"))
            writeManifest(staging.resolve("manifest.properties"), sourceVersion, targetVersion)
            val published = backupRoot.resolve("snapshot-${Instant.now().toEpochMilli()}-${UUID.randomUUID()}")
            return Files.move(staging, published)
        } catch (failure: Exception) {
            runCatching { deleteTree(staging) }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun hasUserTables(): Boolean =
        dataSource.connection.use { connection ->
            connection
                .createStatement()
                .use { statement ->
                    statement
                        .executeQuery(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME <> 'flyway_schema_history'",
                        ).use { result ->
                            result.next() && result.getInt(1) > 0
                        }
                }
        }

    private fun copyWorkspace(target: Path) {
        val source = serverDataRoot.resolve("workspace")
        if (!Files.exists(source)) return
        require(!Files.isSymbolicLink(source)) { "The managed workspace cannot be a symbolic link during backup" }
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    val destination = target.resolve(source.relativize(dir))
                    Files.createDirectories(destination)
                    restrictPermissions(destination, directory = true)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    require(!attrs.isSymbolicLink) { "Managed workspace backup cannot follow symbolic links" }
                    val destination = target.resolve(source.relativize(file))
                    Files.copy(file, destination)
                    restrictPermissions(destination, directory = false)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun writeManifest(
        path: Path,
        sourceVersion: String?,
        targetVersion: String,
    ) {
        val manifest =
            Properties().apply {
                setProperty("formatVersion", "1")
                setProperty("sourceSchemaVersion", sourceVersion ?: "unversioned")
                setProperty("targetSchemaVersion", targetVersion)
                setProperty("createdAt", Instant.now().toString())
            }
        Files.newOutputStream(path).use { manifest.store(it, "Visual Agent pre-migration backup") }
        restrictPermissions(path, directory = false)
    }

    private fun restrictPermissions(
        path: Path,
        directory: Boolean,
    ) {
        if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) == null) return
        val permissions = if (directory) "rwx------" else "rw-------"
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    }

    private fun deleteTree(path: Path) {
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: java.io.IOException?,
                ): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
