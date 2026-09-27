package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientProcessEntry
import de.heckenmann.visualagent.protocol.ClientProcessInventoryDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientProcessInventoryRequest
import de.heckenmann.visualagent.protocol.ClientProcessInventorySnapshot

/** Captures all process identifiers and operating-system-reported commands from the desktop host. */
class JvmClientProcessInventoryDiagnosticsPort : ClientProcessInventoryDiagnosticsPort {
    override fun snapshot(request: ClientProcessInventoryRequest): ClientProcessInventorySnapshot {
        val processIds =
            ProcessHandle.allProcesses().use { stream ->
                stream.map(ProcessHandle::pid).sorted().toList()
            }
        val selectedIds =
            when (request.action) {
                LIST_ACTION -> processIds.drop(request.offset).take(request.pageSize)
                SHOW_ACTION -> listOfNotNull(request.pid?.takeIf(processIds::contains))
                else -> emptyList()
            }
        val processes =
            selectedIds.mapNotNull { pid ->
                ProcessHandle.of(pid).orElse(null)?.let(::toEntry)
            }
        return ClientProcessInventorySnapshot(
            offset = if (request.action == LIST_ACTION) request.offset else 0,
            totalProcesses = processIds.size,
            processes = processes,
            hasMore = request.action == LIST_ACTION && request.offset + selectedIds.size < processIds.size,
        )
    }

    private fun toEntry(process: ProcessHandle): ClientProcessEntry {
        val info = process.info()
        val arguments = info.arguments().orElse(null)?.toList()
        val command = info.command().orElse(null)
        return ClientProcessEntry(
            pid = process.pid(),
            parentPid = process.parent().map { it.pid() }.orElse(null),
            startEpochMillis = info.startInstant().orElse(null)?.toEpochMilli(),
            commandLine = info.commandLine().orElse(null),
            command = command,
            arguments = arguments,
        )
    }

    private companion object {
        const val LIST_ACTION = "list"
        const val SHOW_ACTION = "show"
    }
}
