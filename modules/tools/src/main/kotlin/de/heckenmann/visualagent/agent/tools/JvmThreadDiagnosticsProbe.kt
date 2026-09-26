package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean

/** Implements bounded thread diagnostics with the standard JVM management API. */
@Component
class JvmThreadDiagnosticsProbe : ThreadDiagnosticsProbe {
    override fun inspect(request: ThreadDiagnosticsRequest): ThreadDiagnosticsResult {
        val bean = ManagementFactory.getThreadMXBean()
        val allIds = bean.allThreadIds.sorted()
        val scannedIds = allIds.take(MAX_SCANNED_THREADS)
        val truncatedScan = allIds.size > scannedIds.size
        val deadlockedIds = runCatching { bean.findDeadlockedThreads()?.toList() }.getOrNull()
        val summary = summary(bean, scannedIds, deadlockedIds?.size)
        val entries: List<ThreadEntry>
        val matchingThreads: Int
        val scanWasTruncated: Boolean
        when (request.action) {
            "summary" -> {
                entries = emptyList()
                matchingThreads = 0
                scanWasTruncated = truncatedScan
            }
            "deadlocks" -> {
                val ids = deadlockedIds.orEmpty()
                val info = threadInfo(bean, ids.take(MAX_SCANNED_THREADS), request.maxFrames)
                entries = info.take(request.maxThreads).map(::toEntry)
                matchingThreads = ids.size
                scanWasTruncated = ids.size > request.maxThreads || info.size < minOf(ids.size, MAX_SCANNED_THREADS)
            }
            "dump" -> {
                val info = threadInfo(bean, scannedIds, request.maxFrames)
                val matching = info.filter { request.state == null || it.threadState.name == request.state }
                entries = matching.take(request.maxThreads).map(::toEntry)
                matchingThreads = matching.size
                scanWasTruncated = truncatedScan || matching.size > request.maxThreads
            }
            else -> error("Unsupported thread diagnostic action")
        }
        return ThreadDiagnosticsResult(request.action, summary, entries, matchingThreads, scanWasTruncated)
    }

    private fun summary(
        bean: ThreadMXBean,
        ids: List<Long>,
        deadlockedThreads: Int?,
    ): ThreadSummary {
        val counts =
            threadInfo(bean, ids, 0)
                .groupingBy { it.threadState.name }
                .eachCount()
        return ThreadSummary(bean.threadCount, bean.daemonThreadCount, bean.peakThreadCount, counts, deadlockedThreads)
    }

    private fun threadInfo(
        bean: ThreadMXBean,
        ids: List<Long>,
        maxFrames: Int,
    ): List<ThreadInfo> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            bean.getThreadInfo(ids.toLongArray(), maxFrames).filterNotNull()
        }

    private fun toEntry(info: ThreadInfo): ThreadEntry =
        ThreadEntry(
            id = info.threadId,
            name = info.threadName.take(MAX_NAME_LENGTH),
            state = info.threadState.name,
            lock = info.lockInfo?.toString()?.take(MAX_LOCK_LENGTH),
            lockOwner = info.lockOwnerName?.take(MAX_NAME_LENGTH),
            stack = info.stackTrace.take(MAX_STACK_FRAMES).map(::formatFrame),
        )

    private fun formatFrame(frame: StackTraceElement): String =
        buildString {
            append(frame.className.take(MAX_FRAME_COMPONENT_LENGTH))
            append('.')
            append(frame.methodName.take(MAX_FRAME_COMPONENT_LENGTH))
            append('(')
            append(frame.fileName?.take(MAX_FRAME_COMPONENT_LENGTH) ?: "Unknown Source")
            if (frame.lineNumber >= 0) append(":${frame.lineNumber}")
            append(')')
        }

    private companion object {
        const val MAX_SCANNED_THREADS = 4096
        const val MAX_STACK_FRAMES = 32
        const val MAX_NAME_LENGTH = 160
        const val MAX_LOCK_LENGTH = 192
        const val MAX_FRAME_COMPONENT_LENGTH = 160
    }
}
