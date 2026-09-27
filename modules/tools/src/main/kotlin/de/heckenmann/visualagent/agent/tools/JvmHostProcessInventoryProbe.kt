package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.util.Locale

/** Uses the JDK Process API to read process IDs and commands visible to the current OS account. */
@Component
class JvmHostProcessInventoryProbe : HostProcessInventoryProbe {
    override fun inspect(request: ProcessInventoryRequest): ProcessInventorySnapshot {
        val processIds = ProcessHandle.allProcesses().use { stream -> stream.map(ProcessHandle::pid).sorted().toList() }
        if (request.action == SHOW_ACTION) {
            val matches =
                listOfNotNull(
                    request.pid
                        ?.let(ProcessHandle::of)
                        ?.orElse(null)
                        ?.let(::entry),
                )
            return ProcessInventorySnapshot(request.action, 0, matches.size, matches, false)
        }
        if (request.action == FILTER_ACTION) {
            val processes = processIds.asSequence().mapNotNull { pid -> ProcessHandle.of(pid).orElse(null)?.let(::entry) }
            return filterProcessInventory(processes, request)
        }
        val selectedIds = processIds.drop(request.offset).take(request.pageSize)
        val page = selectedIds.mapNotNull { pid -> ProcessHandle.of(pid).orElse(null)?.let(::entry) }
        return ProcessInventorySnapshot(
            action = request.action,
            offset = request.offset,
            totalProcesses = processIds.size,
            processes = page,
            hasMore = request.offset + selectedIds.size < processIds.size,
        )
    }

    private fun entry(process: ProcessHandle): HostProcessEntry {
        val info = process.info()
        val arguments = info.arguments().orElse(null)?.toList()
        val command = info.command().orElse(null)
        return HostProcessEntry(
            pid = process.pid(),
            parentPid = process.parent().map(ProcessHandle::pid).orElse(null),
            startEpochMillis = info.startInstant().orElse(null)?.toEpochMilli(),
            commandLine = info.commandLine().orElse(null),
            command = command,
            arguments = arguments,
        )
    }

    private companion object {
        const val FILTER_ACTION = "filter"
        const val SHOW_ACTION = "show"
    }
}

internal fun HostProcessEntry.matchesCommandQuery(query: String): Boolean =
    sequenceOf(commandLine, command)
        .filterNotNull()
        .plus(arguments.orEmpty().asSequence())
        .any { query in it.lowercase(Locale.ROOT) }

internal fun filterProcessInventory(
    processes: Sequence<HostProcessEntry>,
    request: ProcessInventoryRequest,
): ProcessInventorySnapshot {
    val query = requireNotNull(request.query).lowercase(Locale.ROOT)
    val page = ArrayList<HostProcessEntry>(request.pageSize)
    var matchCount = 0
    processes.forEach { process ->
        if (process.matchesCommandQuery(query)) {
            if (matchCount >= request.offset && page.size < request.pageSize) page.add(process)
            matchCount++
        }
    }
    return ProcessInventorySnapshot(request.action, request.offset, matchCount, page, request.offset + page.size < matchCount)
}
