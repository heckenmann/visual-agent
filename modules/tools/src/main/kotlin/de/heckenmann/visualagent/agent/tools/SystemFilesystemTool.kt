package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Reports bounded storage capacity for selected Visual Agent server locations. */
@AgentTool
class SystemFilesystemTool(
    private val provider: FilesystemSnapshotProvider,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect capacity and access status for the Visual Agent server data, workspace, database, and temporary locations. " +
                    "The tool does not enumerate arbitrary mounts or return filesystem paths. Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val locations =
            runCatching { provider.snapshot() }
                .getOrElse { return failure(TOOL_ID.value, "Server storage diagnostics are unavailable.") }
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                putJsonArray("locations") {
                    locations.sortedBy(FilesystemLocationSnapshot::id).forEach { item ->
                        add(
                            buildJsonObject {
                                put("id", item.id)
                                put("exists", item.exists)
                                item.readable?.let { put("readable", it) }
                                item.writable?.let { put("writable", it) }
                                item.parentWritable?.let { put("parentWritable", it) }
                                item.storeType?.let { put("storeType", it) }
                                item.storeName?.let { put("storeName", it) }
                                item.readOnly?.let { put("readOnly", it) }
                                item.totalBytes?.let { put("totalBytes", it) }
                                item.usableBytes?.let { put("usableBytes", it) }
                                item.unallocatedBytes?.let { put("unallocatedBytes", it) }
                                item.error?.let { put("error", it) }
                            },
                        )
                    }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:filesystem")
    }
}

/** Logical storage location known to the Visual Agent server. */
data class FilesystemLocation(
    /** Stable logical ID; never a filesystem path. */
    val id: String,
    /** Server-owned path used only for local capacity checks. */
    val path: Path,
)

/** Source of server-owned storage paths. */
fun interface FilesystemLocationProvider {
    /** Return only the named locations relevant to Visual Agent operation. */
    fun locations(): List<FilesystemLocation>
}

/** Capacity and access details for one logical server storage location. */
data class FilesystemLocationSnapshot(
    /** Stable logical location ID. */
    val id: String,
    /** Whether the target location exists. */
    val exists: Boolean,
    /** Whether the target is readable when it exists. */
    val readable: Boolean?,
    /** Whether the target is writable when it exists. */
    val writable: Boolean?,
    /** Whether the nearest existing parent can be written when the target is missing. */
    val parentWritable: Boolean?,
    /** Filesystem type when exposed by the JVM. */
    val storeType: String?,
    /** Filesystem store name when exposed by the JVM. */
    val storeName: String?,
    /** Whether the backing filesystem is read-only. */
    val readOnly: Boolean?,
    /** Total capacity in bytes. */
    val totalBytes: Long?,
    /** Usable bytes available to the Visual Agent process. */
    val usableBytes: Long?,
    /** Unallocated bytes on the backing store. */
    val unallocatedBytes: Long?,
    /** Normalized local failure category. */
    val error: String?,
)

/** Provides bounded filesystem snapshots for selected Visual Agent locations. */
fun interface FilesystemSnapshotProvider {
    /** Inspect the selected server storage locations without returning their paths. */
    fun snapshot(): List<FilesystemLocationSnapshot>
}

/** Reads storage capacity and access flags through standard NIO APIs. */
@Component
class JvmFilesystemSnapshotProvider(
    private val locations: FilesystemLocationProvider,
) : FilesystemSnapshotProvider {
    override fun snapshot(): List<FilesystemLocationSnapshot> = locations.locations().take(MAX_LOCATIONS).map(::inspect)

    private fun inspect(location: FilesystemLocation): FilesystemLocationSnapshot {
        val target = location.path.toAbsolutePath().normalize()
        val exists = Files.exists(target)
        val readable = exists.takeIf { it }?.let { Files.isReadable(target) }
        val writable = exists.takeIf { it }?.let { Files.isWritable(target) }
        val parent = if (exists) target else nearestExistingParent(target)
        val parentWritable = if (exists || parent == null) null else Files.isWritable(parent)
        if (parent == null) return unavailable(location.id, exists, readable, writable, parentWritable, "storage_unavailable")
        return try {
            val store = Files.getFileStore(parent)
            FilesystemLocationSnapshot(
                id = location.id,
                exists = exists,
                readable = readable,
                writable = writable,
                parentWritable = parentWritable,
                storeType = store.type().take(MAX_STORE_VALUE_LENGTH),
                storeName = store.name().take(MAX_STORE_VALUE_LENGTH),
                readOnly = store.isReadOnly,
                totalBytes = store.totalSpace,
                usableBytes = store.usableSpace,
                unallocatedBytes = store.unallocatedSpace,
                error = null,
            )
        } catch (_: IOException) {
            unavailable(location.id, exists, readable, writable, parentWritable, "storage_unavailable")
        } catch (_: SecurityException) {
            unavailable(location.id, exists, readable, writable, parentWritable, "access_denied")
        }
    }

    private fun nearestExistingParent(path: Path): Path? {
        var candidate: Path? = path.parent
        while (candidate != null && !Files.exists(candidate)) candidate = candidate.parent
        return candidate
    }

    private fun unavailable(
        id: String,
        exists: Boolean,
        readable: Boolean?,
        writable: Boolean?,
        parentWritable: Boolean?,
        error: String,
    ) = FilesystemLocationSnapshot(
        id = id,
        exists = exists,
        readable = readable,
        writable = writable,
        parentWritable = parentWritable,
        storeType = null,
        storeName = null,
        readOnly = null,
        totalBytes = null,
        usableBytes = null,
        unallocatedBytes = null,
        error = error,
    )

    private companion object {
        const val MAX_LOCATIONS = 16
        const val MAX_STORE_VALUE_LENGTH = 96
    }
}
