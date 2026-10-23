package de.heckenmann.visualagent.agent.codexdirect

import com.sun.management.ThreadMXBean
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import java.lang.management.ManagementFactory
import java.util.Locale

@Volatile
private var benchmarkResult: Any? = null

/** Runs explicitly requested local overhead measurements, never inference or real-account operations. */
fun main() {
    println("Local-only overhead benchmark; no API calls, no latency pass/fail thresholds.")
    println("Cold/warm compare the current implementation, not historical end-to-end provider latency.")
    benchmark("cold transport acquisition") {
        val pool = DirectTransportPool()
        try {
            val lease = DirectResponsesModels(pool = pool).open("synthetic", "synthetic-model")
            lease.close()
            lease.model
        } finally {
            pool.destroy()
        }
    }
    val pool = DirectTransportPool()
    try {
        val models = DirectResponsesModels(pool = pool)
        models.open("synthetic", "synthetic-model").close()
        benchmark("warm transport acquisition") {
            val lease = models.open("synthetic", "synthetic-model")
            lease.close()
            lease.model
        }
    } finally {
        pool.destroy()
    }
    val file =
        Media
            .builder()
            .mimeType(MimeType.valueOf("application/pdf"))
            .name("synthetic.pdf")
            .data(ByteArrayResource(ByteArray(1024 * 1024) { 42 }))
            .build()
    val message = UserMessage.builder().media(file).build()
    benchmark("1 MiB attachment, repeated encoding") {
        listOf(directFileInputItem(message), directFileInputItem(message))
    }
    benchmark("1 MiB attachment, prepared encoding") {
        val prepared = DirectPreparedAttachment(file)
        listOf(directFileInputItem(message, prepared), directFileInputItem(message, prepared))
    }
}

private fun benchmark(
    label: String,
    action: () -> Any?,
) {
    repeat(10) { benchmarkResult = action() }
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    val allocation = bean?.takeIf { it.isThreadAllocatedMemorySupported }
    allocation?.isThreadAllocatedMemoryEnabled = true
    val thread = Thread.currentThread().threadId()
    val times = mutableListOf<Long>()
    val allocations = mutableListOf<Long>()
    repeat(50) {
        val beforeBytes = allocation?.getThreadAllocatedBytes(thread)
        val start = System.nanoTime()
        benchmarkResult = action()
        times += System.nanoTime() - start
        beforeBytes?.let { allocations += checkNotNull(allocation).getThreadAllocatedBytes(thread) - it }
    }
    times.sort()
    val median = times[times.size / 2] / 1_000_000.0
    val p95 = times[(times.size * 0.95).toInt()] / 1_000_000.0
    val bytes = if (allocations.isEmpty()) "unavailable" else "${allocations.average().toLong()} bytes/op"
    println(String.format(Locale.ROOT, "%s: median=%.3f ms p95=%.3f ms allocation=%s n=%d", label, median, p95, bytes, times.size))
}
