package de.heckenmann.visualagent.agent.tools

import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
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
    ): List<InetAddress>

    /** Resolve PTR names for one numeric address using the system or requested DNS server. */
    fun reverse(
        address: InetAddress,
        dnsServer: DnsServerEndpoint?,
    ): List<String>
}

/** Uses the system resolver by default and dnsjava when an explicit server is selected. */
class JvmHostResolver : HostResolver {
    override fun resolve(
        host: String,
        dnsServer: DnsServerEndpoint?,
    ): List<InetAddress> {
        if (dnsServer == null) return InetAddress.getAllByName(host).toList()
        val resolver = createResolver(dnsServer)
        return listOf(Type.A, Type.AAAA).flatMap { type ->
            val queryName = Name.fromString("$host.")
            val query = Message.newQuery(Record.newRecord(queryName, type, DClass.IN))
            resolver.send(query).getSection(Section.ANSWER).mapNotNull { record ->
                when (record) {
                    is ARecord -> record.address
                    is AAAARecord -> record.address
                    else -> null
                }
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

    private fun createResolver(dnsServer: DnsServerEndpoint): SimpleResolver =
        SimpleResolver(InetSocketAddress(dnsServer.address, dnsServer.port)).apply {
            timeout = Duration.ofSeconds(DNS_TIMEOUT_SECONDS)
        }

    private companion object {
        const val DNS_TIMEOUT_SECONDS = 5L
    }
}

private const val MAX_HOST_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63
