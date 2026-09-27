package de.heckenmann.visualagent.agent.tools

import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmHostResolverTest {
    private val server = DnsServerEndpoint(InetAddress.getByName("192.0.2.53"), 53)

    @Test
    fun `explicit ipv4 lookup never queries failing ipv6 family`() {
        val types = mutableListOf<Int>()
        val resolver =
            resolver { query ->
                types += query.question.type
                if (query.question.type == Type.AAAA) throw IOException("IPv6 query unavailable")
                reply(query, ARecord(query.question.name, DClass.IN, 60, InetAddress.getByName("192.0.2.10")))
            }

        val result = JvmHostResolver { resolver }.resolve("example.org", server, "ipv4")

        assertEquals(listOf(Type.A), types)
        assertEquals(listOf("192.0.2.10"), result.map { it.hostAddress })
    }

    @Test
    fun `explicit lookup follows cname to target address`() {
        val queries = mutableListOf<String>()
        val alias = Name.fromString("alias.example.")
        val target = Name.fromString("target.example.")
        val resolver =
            resolver { query ->
                queries += query.question.name.toString()
                when (query.question.name) {
                    alias -> reply(query, CNAMERecord(alias, DClass.IN, 60, target))
                    target -> reply(query, ARecord(target, DClass.IN, 60, InetAddress.getByName("192.0.2.11")))
                    else -> error("Unexpected query")
                }
            }

        val result = JvmHostResolver { resolver }.resolve("alias.example", server, "ipv4")

        assertEquals(listOf("alias.example.", "target.example."), queries)
        assertEquals(listOf("192.0.2.11"), result.map { it.hostAddress })
    }

    @Test
    fun `numeric literals do not query explicit server`() {
        val resolver = resolver { error("DNS must not be called for a literal") }
        val hostResolver = JvmHostResolver { resolver }

        assertEquals(listOf("192.0.2.12"), hostResolver.resolve("192.0.2.12", server, "ipv4").map { it.hostAddress })
        assertEquals(emptyList(), hostResolver.resolve("192.0.2.12", server, "ipv6"))
        assertEquals(listOf(InetAddress.getByName("2001:db8::12")), hostResolver.resolve("2001:db8::12", server, "ipv6"))
    }

    private fun resolver(response: (Message) -> Message): SimpleResolver =
        object : SimpleResolver(InetSocketAddress(server.address, server.port)) {
            override fun send(query: Message): Message = response(query)
        }

    private fun reply(
        query: Message,
        answer: org.xbill.DNS.Record,
    ): Message =
        Message(query.header.id).apply {
            header.setFlag(Flags.QR.toInt())
            addRecord(query.question, Section.QUESTION)
            addRecord(answer, Section.ANSWER)
        }
}
