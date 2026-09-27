package de.heckenmann.visualagent.agent.tools

/** Parses the bounded routing-table formats emitted by the supported operating systems. */
object RouteTableOutputParser {
    /** Converts native route rows into normalized IPv4/IPv6 route records. */
    fun parse(
        platform: RouteTablePlatform,
        family: String,
        output: String,
    ): List<NormalizedRoute> =
        when (platform) {
            RouteTablePlatform.LINUX -> parseLinux(family, output)
            RouteTablePlatform.MACOS -> parseMacOs(family, output)
            RouteTablePlatform.WINDOWS -> parseWindows(family, output)
        }

    private fun parseLinux(
        family: String,
        output: String,
    ): List<NormalizedRoute> =
        output
            .lineSequence()
            .mapNotNull { line ->
                val fields = line.trim().split(WHITESPACE)
                if (fields.isEmpty() || fields.first() in LINUX_ROUTE_QUALIFIERS) return@mapNotNull null
                val destinationIndex = if (fields.first() == "default") 0 else fields.indexOfFirst(::isDestination)
                if (destinationIndex < 0) return@mapNotNull null
                val destination = normalizeDestination(fields[destinationIndex], family)
                val gateway = fields.valueAfter("via") ?: "on-link"
                NormalizedRoute(
                    destination = destination,
                    gateway = gateway,
                    networkInterface = fields.valueAfter("dev").orEmpty(),
                    metric = fields.valueAfter("metric")?.toLongOrNull(),
                    defaultRoute = destination == defaultDestination(family),
                )
            }.toList()

    private fun parseMacOs(
        family: String,
        output: String,
    ): List<NormalizedRoute> =
        output
            .lineSequence()
            .mapNotNull { line ->
                val fields = line.trim().split(WHITESPACE)
                if (fields.size < 4 || fields.first() in MACOS_HEADERS) return@mapNotNull null
                val destination = normalizeDestination(fields[0], family)
                if (!isDestination(destination) && !isMacOsIpv4Network(destination)) return@mapNotNull null
                NormalizedRoute(
                    destination = destination,
                    gateway = fields[1],
                    networkInterface = fields[3],
                    defaultRoute = destination == defaultDestination(family),
                )
            }.toList()

    private fun parseWindows(
        family: String,
        output: String,
    ): List<NormalizedRoute> {
        return output
            .lineSequence()
            .mapNotNull { line ->
                val fields = line.trim().split(WHITESPACE)
                if (fields.size < 4 || fields.first() in WINDOWS_HEADERS) return@mapNotNull null
                if (family == IPV6) parseWindowsIpv6(fields) else parseWindowsIpv4(fields)
            }.toList()
    }

    private fun parseWindowsIpv4(fields: List<String>): NormalizedRoute? {
        if (fields.size < 5 || !isIpv4(fields[0]) || !isIpv4(fields[1]) || !isIpv4(fields[2])) return null
        val destination = ipv4Prefix(fields[0], fields[1]) ?: return null
        return NormalizedRoute(
            destination = destination,
            gateway = fields[2],
            networkInterface = fields[3],
            metric = fields[4].toLongOrNull(),
            defaultRoute = destination == defaultDestination(IPV4),
        )
    }

    private fun parseWindowsIpv6(fields: List<String>): NormalizedRoute? {
        if (fields.size < 4) return null
        val destinationIndex = if (fields[0].toLongOrNull() != null && fields[1].toLongOrNull() != null) 2 else 0
        val destination = normalizeDestination(fields[destinationIndex], IPV6)
        if (!isDestination(destination)) return null
        val hasIndexAndMetric = destinationIndex == 2
        return NormalizedRoute(
            destination = destination,
            gateway = fields[destinationIndex + 1],
            networkInterface = if (hasIndexAndMetric) fields[0] else "",
            metric = if (hasIndexAndMetric) fields[1].toLongOrNull() else null,
            defaultRoute = destination == defaultDestination(IPV6),
        )
    }

    private fun ipv4Prefix(
        address: String,
        mask: String,
    ): String? {
        val octets = mask.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        val bits = octets.joinToString("") { it.toString(2).padStart(8, '0') }
        if ("01" in bits) return null
        val prefix = bits.count { it == '1' }
        return "$address/$prefix"
    }

    private fun normalizeDestination(
        destination: String,
        family: String,
    ): String =
        when {
            destination == "default" -> defaultDestination(family)
            destination == "0.0.0.0" && family == IPV4 -> "0.0.0.0/0"
            destination == "::" && family == IPV6 -> "::/0"
            else -> destination
        }

    private fun defaultDestination(family: String) = if (family == IPV4) "0.0.0.0/0" else "::/0"

    private fun isDestination(value: String): Boolean = isIpv4(value) || value.contains(':') || value.contains('/') || value == "default"

    private fun isIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
    }

    private fun isMacOsIpv4Network(value: String): Boolean {
        val octets = value.split('.')
        return octets.size in 1..3 && octets.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
    }

    private fun List<String>.valueAfter(key: String): String? {
        val index = indexOf(key)
        return if (index >= 0) getOrNull(index + 1) else null
    }

    private const val IPV4 = "ipv4"
    private const val IPV6 = "ipv6"
    private val WHITESPACE = Regex("\\s+")
    private val LINUX_ROUTE_QUALIFIERS = setOf("unreachable", "prohibit", "blackhole", "throw")
    private val MACOS_HEADERS = setOf("Destination", "Internet:", "Internet6:", "Routing")
    private val WINDOWS_HEADERS = setOf("Network", "If", "Persistent")
}
