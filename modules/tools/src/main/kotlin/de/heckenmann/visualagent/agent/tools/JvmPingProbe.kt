package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.ceil

/** Uses [InetAddress.isReachable] for bounded server-side reachability measurements. */
@Component
class JvmReachabilityChecker : ReachabilityChecker {
    override fun isReachable(
        address: InetAddress,
        timeoutMillis: Int,
    ): Boolean = address.isReachable(timeoutMillis)
}

/** Supplies monotonic elapsed-time measurements without wall-clock adjustments. */
@Component
class SystemMonotonicTimeSource : MonotonicTimeSource {
    override fun nanoTime(): Long = System.nanoTime()
}

/** Implements the ping-like probe with JVM name resolution and reachability APIs only. */
@Component
class JvmPingProbe(
    private val resolver: HostResolver,
    private val reachabilityChecker: ReachabilityChecker,
    private val timeSource: MonotonicTimeSource,
) : PingProbe {
    override fun ping(
        host: String,
        family: String,
        count: Int,
        timeoutSeconds: Int,
    ): PingProbeResult {
        val start = timeSource.nanoTime()
        val deadline = start + timeoutSeconds * NANOS_PER_SECOND
        val target =
            try {
                firstAddressForFamily(resolver.resolve(host, null), family)
            } catch (_: UnknownHostException) {
                return emptyResult("dns_failure", start)
            } catch (_: Exception) {
                return emptyResult("dns_failure", start)
            } ?: return emptyResult("family_unavailable", start)

        val responseTimes = mutableListOf<Long>()
        var completed = 0
        var deadlineReached = false
        repeat(count) {
            val remainingNanos = deadline - timeSource.nanoTime()
            if (remainingNanos <= 0) {
                deadlineReached = true
                return@repeat
            }
            val attemptStart = timeSource.nanoTime()
            val timeoutMillis = ceil(remainingNanos.toDouble() / NANOS_PER_MILLI).toInt().coerceAtLeast(1)
            val reachable =
                try {
                    reachabilityChecker.isReachable(target, timeoutMillis)
                } catch (_: IOException) {
                    false
                }
            val elapsed = (timeSource.nanoTime() - attemptStart).coerceAtLeast(0)
            completed++
            if (reachable) responseTimes += elapsed
            if (deadline - timeSource.nanoTime() <= 0 && completed < count) deadlineReached = true
        }
        val successful = responseTimes.size
        val total = responseTimes.sum()
        return PingProbeResult(
            status = if (successful > 0) "completed" else "no_response",
            resolvedAddress = target.hostAddress,
            attemptsCompleted = completed,
            reachableResponses = successful,
            unansweredPercent = if (completed == 0) null else (completed - successful) * 100.0 / completed,
            minRttMillis = responseTimes.minOrNull()?.toMillisAsDouble(),
            averageRttMillis = responseTimes.takeIf { it.isNotEmpty() }?.let { total.toDouble() / it.size / NANOS_PER_MILLI },
            maxRttMillis = responseTimes.maxOrNull()?.toMillisAsDouble(),
            elapsedMillis = (timeSource.nanoTime() - start).coerceAtLeast(0) / NANOS_PER_MILLI,
            deadlineReached = deadlineReached,
        )
    }

    private fun firstAddressForFamily(
        addresses: List<InetAddress>,
        family: String,
    ): InetAddress? =
        addresses
            .asSequence()
            .filter {
                when (family) {
                    "ipv4" -> it is Inet4Address
                    "ipv6" -> it is Inet6Address
                    else -> true
                }
            }.sortedBy(InetAddress::getHostAddress)
            .firstOrNull()

    private fun emptyResult(
        status: String,
        start: Long,
    ): PingProbeResult = PingProbeResult(status, null, 0, 0, null, null, null, null, elapsedSince(start), false)

    private fun elapsedSince(start: Long): Long = (timeSource.nanoTime() - start).coerceAtLeast(0) / NANOS_PER_MILLI

    private fun Long.toMillisAsDouble(): Double = toDouble() / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
