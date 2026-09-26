package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Implements bounded TCP diagnostics with JVM sockets and the shared server-side resolver. */
@Component
class JvmTcpConnectionProbe(
    private val resolver: HostResolver,
) : TcpConnectionProbe {
    override fun connect(
        host: String,
        port: Int,
        family: String,
    ): TcpProbeOutcome {
        val startedAt = System.nanoTime()
        val candidates =
            try {
                resolver
                    .resolve(host, null)
                    .selectFamily(family)
                    .distinctBy(InetAddress::getHostAddress)
                    .sortedBy(InetAddress::getHostAddress)
                    .take(MAX_CANDIDATES)
            } catch (_: UnknownHostException) {
                return outcome(emptyList(), null, false, startedAt, "dns_failure")
            } catch (_: Exception) {
                return outcome(emptyList(), null, false, startedAt, "dns_failure")
            }
        if (candidates.isEmpty()) return outcome(emptyList(), null, false, startedAt, "dns_failure")

        var lastFailure = "connection_failed"
        for (address in candidates) {
            val remainingMillis = (MAX_DURATION_MILLIS - elapsedMillis(startedAt)).coerceAtLeast(0)
            if (remainingMillis == 0L) {
                lastFailure = "timeout"
                break
            }
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), remainingMillis.toInt())
                }
                return outcome(candidates, address.hostAddress, true, startedAt, null)
            } catch (_: SocketTimeoutException) {
                lastFailure = "timeout"
            } catch (_: ConnectException) {
                lastFailure = "connection_refused"
            } catch (_: NoRouteToHostException) {
                lastFailure = "network_unreachable"
            } catch (_: Exception) {
                lastFailure = "connection_failed"
            }
        }
        return outcome(candidates, null, false, startedAt, lastFailure)
    }

    private fun List<InetAddress>.selectFamily(family: String): List<InetAddress> =
        when (family) {
            "ipv4" -> filterIsInstance<Inet4Address>()
            "ipv6" -> filterIsInstance<Inet6Address>()
            else -> this
        }

    private fun outcome(
        candidates: List<InetAddress>,
        connectedAddress: String?,
        connected: Boolean,
        startedAt: Long,
        failure: String?,
    ): TcpProbeOutcome =
        TcpProbeOutcome(
            resolvedAddresses = candidates.map(InetAddress::getHostAddress),
            connectedAddress = connectedAddress,
            connected = connected,
            durationMillis = elapsedMillis(startedAt),
            failureCategory = failure,
        )

    private fun elapsedMillis(startedAt: Long): Long = ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0)

    private companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_DURATION_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
