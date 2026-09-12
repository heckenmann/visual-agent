package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.ServerDirectoryPickerEntry
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerPage
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asSequence

/**
 * Direct-user server filesystem browser that exposes short-lived opaque selections.
 *
 * Entries are returned in bounded pages. The server resolves the selection again when a grant is
 * created, so a stale browser result cannot authorize an arbitrary submitted host path.
 */
@Service
class ServerDirectoryBrowserService {
    private val selections = ConcurrentHashMap<String, Selection>()
    private val pageCursors = ConcurrentHashMap<String, PageCursor>()

    /** Lists a bounded page of readable server filesystem roots. */
    fun roots(pageToken: String? = null): ServerDirectoryPickerPage =
        page(targetKey = ROOT_TARGET, pageToken = pageToken) {
            FileSystems.getDefault().rootDirectories.mapNotNull { root -> runCatching { root.toRealPath() }.getOrNull() }
        }

    /** Lists a bounded page of readable child directories beneath an opaque selection. */
    fun children(
        selectionId: String,
        pageToken: String? = null,
    ): ServerDirectoryPickerPage {
        val parent = resolve(selectionId)
        return page(targetKey = "children:$selectionId", pageToken = pageToken) {
            Files
                .list(parent)
                .use { paths ->
                    paths
                        .asSequence()
                        .filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(it) }
                        .mapNotNull { path -> runCatching { path.toRealPath() }.getOrNull() }
                        .toList()
                }
        }
    }

    /** Resolves and revalidates one opaque selection before it becomes a persistent grant. */
    fun resolve(selectionId: String): Path {
        expireStaleState()
        val selected = requireNotNull(selections[selectionId]) { "Unknown or expired server directory selection" }
        val real = selected.path.toRealPath()
        require(Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) { "Selected server path is not a directory" }
        require(Files.isReadable(real)) { "Selected server directory is not readable" }
        selections[selectionId] = selected.copy(expiresAt = expiresAt())
        return real
    }

    private fun page(
        targetKey: String,
        pageToken: String?,
        paths: () -> List<Path>,
    ): ServerDirectoryPickerPage {
        expireStaleState()
        val offset = pageToken?.let { token -> requirePageOffset(token, targetKey) } ?: 0
        val sorted = paths().distinct().sortedBy { it.fileName?.toString() ?: it.toString() }
        val pagePaths = sorted.drop(offset).take(PAGE_SIZE)
        val entries = pagePaths.map(::register)
        val nextOffset = offset + pagePaths.size
        return ServerDirectoryPickerPage(entries, nextPageToken(targetKey, nextOffset, sorted.size))
    }

    private fun requirePageOffset(
        pageToken: String,
        targetKey: String,
    ): Int {
        val cursor = requireNotNull(pageCursors.remove(pageToken)) { "Unknown or expired server directory page" }
        require(cursor.targetKey == targetKey) { "Invalid server directory page" }
        require(cursor.expiresAt.isAfter(Instant.now())) { "Expired server directory page" }
        return cursor.offset
    }

    private fun nextPageToken(
        targetKey: String,
        nextOffset: Int,
        total: Int,
    ): String? {
        if (nextOffset >= total) return null
        return UUID.randomUUID().toString().also { token ->
            pageCursors[token] = PageCursor(targetKey, nextOffset, expiresAt())
        }
    }

    private fun register(path: Path): ServerDirectoryPickerEntry {
        val canonical = path.toRealPath()
        require(Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)) { "Server path is not a directory" }
        require(Files.isReadable(canonical)) { "Server directory is not readable" }
        val selectionId = UUID.randomUUID().toString()
        selections[selectionId] = Selection(canonical, expiresAt())
        return ServerDirectoryPickerEntry(
            selectionId = selectionId,
            displayName = canonical.fileName?.toString() ?: canonical.toString(),
            location = canonical.toString(),
            parentSelectionId = null,
        )
    }

    private fun expireStaleState() {
        val now = Instant.now()
        selections.entries.removeIf { (_, selection) -> !selection.expiresAt.isAfter(now) }
        pageCursors.entries.removeIf { (_, cursor) -> !cursor.expiresAt.isAfter(now) }
    }

    private fun expiresAt(): Instant = Instant.now().plus(SELECTION_TTL)

    private data class Selection(
        val path: Path,
        val expiresAt: Instant,
    )

    private data class PageCursor(
        val targetKey: String,
        val offset: Int,
        val expiresAt: Instant,
    )

    private companion object {
        const val ROOT_TARGET = "roots"
        const val PAGE_SIZE = 100
        val SELECTION_TTL: Duration = Duration.ofMinutes(5)
    }
}
