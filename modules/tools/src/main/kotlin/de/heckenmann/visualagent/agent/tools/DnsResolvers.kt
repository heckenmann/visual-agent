package de.heckenmann.visualagent.agent.tools

import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Lookup
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.ReverseMap
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.time.Duration

/** Validates a DNS hostname or numeric IP literal without accepting URLs or path syntax. */
internal fun normalizeNetworkHost(value: String): String {
    val host = value.trim().removeSuffix(".")
    require(host.isNotEmpty() && host.length <= MAX_HOST_LENGTH)
    require(host.none { it.isWhitespace() || it in "/?#@\\" })
    if (':' in host) {
        require(host.all { it.isLetterOrDigit() || it in ":.%_-" })
        require(InetAddress.getByName(host) is Inet6Address)
        return host.lowercase()
    }
    val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase()
    require(ascii.length <= MAX_HOST_LENGTH && ascii.split('.').all { it.length in 1..MAX_LABEL_LENGTH })
    return ascii
}

/**
 * Destination for an explicit DNS query, validated before reaching the resolver.
 *
 * @property address Numeric DNS server address
 * @property port DNS server port
 */
data class DnsServerEndpoint(
    val address: InetAddress,
    val port: Int,
)

/** Resolves forward and reverse DNS queries for [NetworkDnsTool]. */
interface HostResolver {
    /** Resolve one validated hostname or address literal using the system or requested DNS server. */
    @Throws(UnknownHostException::class)
    fun resolve(
        host: String,
        dnsServer: DnsServerEndpoint?,
        family: String = "all",
    ): List<InetAddress>

    /** Resolve PTR names for one numeric address using the system or requested DNS server. */
    fun reverse(
        address: InetAddress,
        dnsServer: DnsServerEndpoint?,
    ): List<String>
}

/** Uses the system resolver by default and dnsjava when an explicit server is selected. */
class JvmHostResolver(
    private val resolverFactory: (DnsServerEndpoint) -> Resolver = ::createResolver,
) : HostResolver {
    override fun resolve(
        host: String,
        dnsServer: DnsServerEndpoint?,
        family: String,
    ): List<InetAddress> {
        val literal = runCatching { host.toNumericIpAddress() }.getOrNull()
        if (literal != null) return listOf(literal).filterByFamily(family)
        if (dnsServer == null) return InetAddress.getAllByName(host).toList().filterByFamily(family)
        val resolver = resolverFactory(dnsServer)
        val types =
            when (family) {
                "ipv4" -> listOf(Type.A)
                "ipv6" -> listOf(Type.AAAA)
                else -> listOf(Type.A, Type.AAAA)
            }
        val results = types.map { type -> runCatching { lookup(host, type, resolver) } }
        val addresses = results.flatMap { it.getOrNull().orEmpty() }
        if (addresses.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        return addresses
    }

    private fun lookup(
        host: String,
        type: Int,
        resolver: Resolver,
    ): List<InetAddress> {
        val lookup =
            Lookup(Name.fromString("$host."), type).apply {
                setResolver(resolver)
                setCache(null)
                setSearchPath(null as List<Name>?)
                setHostsFileParser(null)
            }
        return lookup.run().orEmpty().mapNotNull { record ->
            when (record) {
                is ARecord -> record.address
                is AAAARecord -> record.address
                else -> null
            }
        }
    }

    override fun reverse(
        address: InetAddress,
        dnsServer: DnsServerEndpoint?,
    ): List<String> {
        if (dnsServer == null) {
            val hostname = InetAddress.getByAddress(address.address).canonicalHostName
            return listOfNotNull(hostname.takeIf { it != address.hostAddress })
        }
        val query = Message.newQuery(Record.newRecord(ReverseMap.fromAddress(address), Type.PTR, DClass.IN))
        return createResolver(dnsServer)
            .send(query)
            .getSection(Section.ANSWER)
            .filterIsInstance<PTRRecord>()
            .map { it.target.toString() }
    }
}

private fun List<InetAddress>.filterByFamily(family: String): List<InetAddress> =
    when (family) {
        "ipv4" -> filter { it !is Inet6Address }
        "ipv6" -> filterIsInstance<Inet6Address>()
        else -> this
    }

private fun createResolver(dnsServer: DnsServerEndpoint): SimpleResolver =
    SimpleResolver(InetSocketAddress(dnsServer.address, dnsServer.port)).apply {
        timeout = Duration.ofSeconds(5)
    }

private const val MAX_HOST_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63
