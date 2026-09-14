package de.heckenmann.visualagent.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** One client-local bookmark for a remote Visual Agent application server. */
@Serializable
data class DesktopServerBookmark(
    val id: String,
    val name: String,
    val visualAgentServerEndpoint: String,
)

/** Versioned bootstrap state stored before the desktop contacts a Visual Agent server. */
@Serializable
data class DesktopServerBookmarkState(
    val version: Int = CURRENT_BOOKMARK_VERSION,
    val visualAgentServerBookmarks: List<DesktopServerBookmark> = emptyList(),
    val lastSelectedVisualAgentServerId: String = LOCAL_SERVER_ID,
)

/** Result of loading client-local Visual Agent server bookmarks. */
sealed interface DesktopServerBookmarkLoadResult {
    /** A valid bookmark state was loaded or the file did not exist yet. */
    data class Loaded(
        val state: DesktopServerBookmarkState,
    ) : DesktopServerBookmarkLoadResult

    /** The source file was preserved but cannot be used until the user repairs the list. */
    data class Invalid(
        val message: String,
    ) : DesktopServerBookmarkLoadResult
}

/**
 * Persists the client-local server bookmarks required before any application server is available.
 *
 * The store deliberately contains only Visual Agent application-server locations. It never stores
 * provider settings, model selections, credentials, or application-server state.
 */
class DesktopServerBookmarkStore(
    private val storageFile: Path,
    private val legacyStorageFile: Path? = null,
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            prettyPrint = true
        },
) {
    /** Creates the production store with the current config location and legacy migration source. */
    constructor() : this(ClientConfigPathResolver.bookmarkFile(), ClientConfigPathResolver.legacyBookmarkFile())

    /** Loads bookmarks without creating a file for a first-time user. */
    fun load(): DesktopServerBookmarkLoadResult {
        val sourceFile =
            when {
                Files.exists(storageFile) -> storageFile
                legacyStorageFile?.let(Files::exists) == true -> legacyStorageFile
                else -> return DesktopServerBookmarkLoadResult.Loaded(DesktopServerBookmarkState())
            }
        return runCatching {
            readValidatedState(sourceFile)
        }.fold(
            onSuccess = { state ->
                val effectiveState =
                    if (sourceFile != storageFile) {
                        try {
                            migrateLegacyState(state)
                        } catch (_: Exception) {
                            return DesktopServerBookmarkLoadResult.Invalid(
                                "The previous server bookmark file was found but could not be migrated to the client config directory.",
                            )
                        }
                    } else {
                        state
                    }
                DesktopServerBookmarkLoadResult.Loaded(effectiveState)
            },
            onFailure = { DesktopServerBookmarkLoadResult.Invalid("Saved server bookmarks are invalid and were left unchanged.") },
        )
    }

    /** Validates and atomically persists a complete bookmark state. */
    fun save(state: DesktopServerBookmarkState) {
        val validated = validateState(state)
        Files.createDirectories(storageFile.parent)
        val temporary = Files.createTempFile(storageFile.parent, storageFile.fileName.toString(), ".tmp")
        try {
            Files.writeString(
                temporary,
                json.encodeToString(validated),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            restrictPermissions(temporary)
            moveAtomically(temporary, storageFile)
            restrictPermissions(storageFile)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateState(state: DesktopServerBookmarkState): DesktopServerBookmarkState {
        require(state.version == CURRENT_BOOKMARK_VERSION) { "Unsupported server bookmark version" }
        val bookmarks = state.visualAgentServerBookmarks.map(::validateBookmark)
        require(bookmarks.map(DesktopServerBookmark::id).distinct().size == bookmarks.size) {
            "Server bookmark identifiers must be unique"
        }
        require(bookmarks.map(DesktopServerBookmark::visualAgentServerEndpoint).distinct().size == bookmarks.size) {
            "Visual Agent server endpoints must be unique"
        }
        require(
            state.lastSelectedVisualAgentServerId == LOCAL_SERVER_ID ||
                bookmarks.any { it.id == state.lastSelectedVisualAgentServerId },
        ) { "The selected Visual Agent server bookmark is missing" }
        return state.copy(visualAgentServerBookmarks = bookmarks.sortedBy(DesktopServerBookmark::name))
    }

    private fun readValidatedState(file: Path): DesktopServerBookmarkState =
        validateState(json.decodeFromString<DesktopServerBookmarkState>(Files.readString(file)))

    private fun validateBookmark(bookmark: DesktopServerBookmark): DesktopServerBookmark {
        require(runCatching { UUID.fromString(bookmark.id) }.isSuccess) { "Server bookmark identifier must be a UUID" }
        require(bookmark.name.trim().isNotEmpty()) { "Server bookmark name is required" }
        val remote = DesktopServerEndpointSelector.parseRemoteEndpoint(bookmark.visualAgentServerEndpoint)
        return bookmark.copy(
            name = bookmark.name.trim(),
            visualAgentServerEndpoint = DesktopServerEndpointSelector.format(remote),
        )
    }

    private fun moveAtomically(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun migrateLegacyState(state: DesktopServerBookmarkState): DesktopServerBookmarkState {
        Files.createDirectories(storageFile.parent)
        val temporary = Files.createTempFile(storageFile.parent, storageFile.fileName.toString(), ".migration.tmp")
        try {
            Files.writeString(
                temporary,
                json.encodeToString(state),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            restrictPermissions(temporary)
            if (!copyFileWithoutReplacement(temporary, storageFile)) {
                return readValidatedState(storageFile)
            }
            restrictPermissions(storageFile)
            return state
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun restrictPermissions(file: Path) {
        runCatching {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        }
    }
}

internal const val LOCAL_SERVER_ID = "local"
private const val CURRENT_BOOKMARK_VERSION = 1

/** Copies a migration file only when the destination does not already exist. */
internal fun copyFileWithoutReplacement(
    source: Path,
    target: Path,
): Boolean =
    runCatching { Files.copy(source, target) }
        .fold(
            onSuccess = { true },
            onFailure = { failure ->
                if (failure is FileAlreadyExistsException) false else throw failure
            },
        )
