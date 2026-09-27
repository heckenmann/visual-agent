package de.heckenmann.visualagent.agent.tools

/** Operating-system command families supported for route-table inspection. */
enum class RouteTablePlatform {
    /** Linux `ip route` command family. */
    LINUX,

    /** macOS `netstat -rn` command family. */
    MACOS,

    /** Windows `route print` command family. */
    WINDOWS,
    ;

    companion object {
        /** Maps the JVM operating-system name to a supported route-table command family. */
        fun fromOsName(osName: String): RouteTablePlatform? =
            when {
                osName.startsWith("Linux", ignoreCase = true) -> LINUX
                osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Darwin", ignoreCase = true) -> MACOS
                osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
                else -> null
            }
    }
}

/** Builds fixed argument vectors for route-table inspection; no model input is accepted. */
object RouteTableCommandBuilder {
    /** Return the platform's commands for one address family or both families. */
    fun commands(
        platform: RouteTablePlatform,
        family: String,
    ): List<Pair<String, List<String>>> {
        require(family in FAMILIES)
        return FAMILIES_TO_QUERY.getValue(family).map { addressFamily ->
            addressFamily to command(platform, addressFamily)
        }
    }

    private fun command(
        platform: RouteTablePlatform,
        family: String,
    ): List<String> =
        when (platform) {
            RouteTablePlatform.LINUX ->
                if (family ==
                    IPV4
                ) {
                    listOf("ip", "-4", "route", "show", "table", "main")
                } else {
                    listOf("ip", "-6", "route", "show", "table", "main")
                }
            RouteTablePlatform.MACOS -> listOf("netstat", "-rn", "-f", if (family == IPV4) "inet" else "inet6")
            RouteTablePlatform.WINDOWS -> listOf("route", "print", if (family == IPV4) "-4" else "-6")
        }

    private const val IPV4 = "ipv4"
    private val FAMILIES = setOf("all", IPV4, "ipv6")
    private val FAMILIES_TO_QUERY = mapOf("all" to listOf(IPV4, "ipv6"), IPV4 to listOf(IPV4), "ipv6" to listOf("ipv6"))
}
