package de.heckenmann.visualagent.agent.codexdirect.benchmark

import com.sun.management.ThreadMXBean
import de.heckenmann.visualagent.agent.codexdirect.DirectSseFixture
import java.lang.management.ManagementFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@Volatile
private var observed: Any? = null

/** Subscription-batch timing; concurrent requests record their first visible text without sleeps. */
internal class NetworkSample {
    private val started = System.nanoTime()
    private val firstText = AtomicLong()

    fun text(value: String) {
        if (value.isNotEmpty()) firstText.compareAndSet(0, System.nanoTime() - started)
    }

    fun firstTextNanos(): Long = firstText.get()
}

/** Reports measurements separately from correctness tests, with no latency-based pass criteria. */
internal fun measureNetwork(
    label: String,
    fixture: DirectSseFixture,
    action: (NetworkSample) -> Any?,
) {
    repeat(10) { observed = action(NetworkSample()) }
    val requests = fixture.requestCount.get()
    val connections = fixture.connections.get()
    val received = fixture.receivedBytes.get()
    val sent = fixture.sentBytes.get()
    val allocation = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf { it.isThreadAllocatedMemorySupported }
    allocation?.isThreadAllocatedMemoryEnabled = true
    val thread = Thread.currentThread().threadId()
    val elapsed = mutableListOf<Long>()
    val first = mutableListOf<Long>()
    val bytes = mutableListOf<Long>()
    repeat(50) {
        val before = allocation?.getThreadAllocatedBytes(thread)
        val start = System.nanoTime()
        val sample = NetworkSample()
        observed = action(sample)
        elapsed += System.nanoTime() - start
        sample.firstTextNanos().takeIf { it > 0 }?.let(first::add)
        before?.let { bytes += checkNotNull(allocation).getThreadAllocatedBytes(thread) - it }
    }
    println(
        "$label: total ${distribution(elapsed)}, first-visible-text ${distribution(first)}, " +
            "caller-thread allocation=${if (bytes.isEmpty()) "unavailable" else bytes.average().toLong()} bytes/batch; " +
            "requests=${fixture.requestCount.get() - requests}, accepted-connections=${fixture.connections.get() - connections}, " +
            "request-body-bytes=${fixture.receivedBytes.get() - received}, response-body-bytes=${fixture.sentBytes.get() - sent}; n=50",
    )
}

private fun distribution(values: List<Long>): String {
    if (values.isEmpty()) return "unavailable"
    val sorted = values.sorted()
    return String.format(
        Locale.ROOT,
        "median=%.3f ms p95=%.3f ms",
        sorted[sorted.size / 2] / 1_000_000.0,
        sorted[(sorted.size * 0.95).toInt()] / 1_000_000.0,
    )
}
