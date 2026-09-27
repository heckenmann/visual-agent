package de.heckenmann.visualagent.agent.tools

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Supported native route-tracing command families. */
enum class TraceroutePlatform {
    /** Linux traceroute executable. */
    LINUX,

    /** macOS traceroute executable, which selects IP version from the numeric destination. */
    MACOS,

    /** Windows tracert executable. */
    WINDOWS,
    ;

    companion object {
        /** Map a JVM operating-system name to a supported platform. */
        fun fromOsName(osName: String): TraceroutePlatform? =
            when {
                osName.startsWith("Linux", ignoreCase = true) -> LINUX
                osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Darwin", ignoreCase = true) -> MACOS
                osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
                else -> null
            }
    }
}

/** Builds fixed, shell-free argument vectors for supported platform route tools. */
object TracerouteCommandBuilder {
    /** Return an executable plus validated options and a numeric destination. */
    fun build(
        platform: TraceroutePlatform,
        target: InetAddress,
        maxHops: Int,
        perHopTimeoutMillis: Int,
    ): List<String> {
        require(maxHops in 1..MAX_HOPS)
        require(perHopTimeoutMillis in 1..MAX_TIMEOUT_MILLIS)
        return when (platform) {
            TraceroutePlatform.LINUX -> linuxCommand(target, maxHops, perHopTimeoutMillis)
            TraceroutePlatform.MACOS -> macCommand(target, maxHops, perHopTimeoutMillis)
            TraceroutePlatform.WINDOWS -> windowsCommand(target, maxHops, perHopTimeoutMillis)
        }
    }

    private fun linuxCommand(
        target: InetAddress,
        maxHops: Int,
        timeoutMillis: Int,
    ): List<String> =
        buildList {
            addAll(listOf("traceroute", "-n", "-q", "1", "-m", maxHops.toString(), "-w", "${timeoutMillis / MILLIS_PER_SECOND}"))
            add(if (target is Inet4Address) "-4" else "-6")
            add(target.hostAddress)
        }

    private fun macCommand(
        target: InetAddress,
        maxHops: Int,
        timeoutMillis: Int,
    ): List<String> {
        val executable = if (target is Inet6Address) "traceroute6" else "traceroute"
        return listOf(
            executable,
            "-n",
            "-q",
            "1",
            "-m",
            maxHops.toString(),
            "-w",
            "${timeoutMillis / MILLIS_PER_SECOND}",
            target.hostAddress,
        )
    }

    private fun windowsCommand(
        target: InetAddress,
        maxHops: Int,
        timeoutMillis: Int,
    ): List<String> =
        buildList {
            addAll(listOf("tracert", "/d", "/h", maxHops.toString(), "/w", timeoutMillis.toString()))
            add(if (target is Inet4Address) "/4" else "/6")
            add(target.hostAddress)
        }

    private const val MAX_HOPS = 30
    private const val MAX_TIMEOUT_MILLIS = 1_000
    private const val MILLIS_PER_SECOND = 1_000
}

/** Parses numeric hop lines from Linux, macOS, and Windows traceroute output. */
object TracerouteOutputParser {
    /** Extract at most [maxHops] normalized rows, ignoring localized banners and summaries. */
    fun parse(
        output: String,
        maxHops: Int,
    ): List<TracerouteHop> {
        require(maxHops in 1..MAX_HOPS)
        return output
            .lineSequence()
            .mapNotNull { line -> parseHop(line, maxHops) }
            .distinctBy(TracerouteHop::number)
            .sortedBy(TracerouteHop::number)
            .take(maxHops)
            .toList()
    }

    private fun parseHop(
        line: String,
        maxHops: Int,
    ): TracerouteHop? {
        val number =
            HOP_NUMBER
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in 1..maxHops } ?: return null
        val latencies = LATENCY.findAll(line).mapNotNull { it.groupValues[1].removePrefix("<").toDoubleOrNull() }.toList()
        val address = ADDRESS.findAll(line).map { it.value.trim('[', ']', '(', ')', ',') }.firstOrNull(::isNumericAddress)
        return TracerouteHop(number, address, latencies, if (address != null || latencies.isNotEmpty()) "responded" else "no_response")
    }

    private fun isNumericAddress(value: String): Boolean =
        when {
            IPV4.matches(value) -> value.split('.').all { octet -> octet.toIntOrNull()?.let { it in 0..255 } == true }
            value.contains(':') && IPV6.matches(value) ->
                runCatching { InetAddress.getByName(value.substringBefore('%')) is Inet6Address }.getOrDefault(false)
            else -> false
        }

    private val HOP_NUMBER = Regex("^\\s*(\\d+)\\s+")
    private val LATENCY = Regex("(<\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)\\s*ms", RegexOption.IGNORE_CASE)
    private val ADDRESS =
        Regex(
            "(?<![0-9A-Fa-f:.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9A-Fa-f:.])|(?<![0-9A-Fa-f:])[0-9A-Fa-f]*:[0-9A-Fa-f:]+(?:%[A-Za-z0-9_.-]+)?(?![0-9A-Fa-f:])",
        )
    private val IPV4 = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
    private val IPV6 = Regex("[0-9A-Fa-f:]+(?:%[A-Za-z0-9_.-]+)?")
    private const val MAX_HOPS = 30
}
