package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Date
import java.util.function.Supplier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Resolves one bounded target and normalizes JVM TLS handshake diagnostics. */
@Component
class JvmTlsDiagnosticProbe(
    private val resolver: HostResolver,
    private val handshakeProbe: TlsHandshakeProbe,
) : TlsDiagnosticProbe {
    override fun inspect(
        host: String,
        port: Int,
        family: String,
        timeoutSeconds: Int,
    ): TlsDiagnosticResult {
        val startedAt = System.nanoTime()
        val addresses =
            try {
                selectFamily(resolver.resolve(host, null), family)
                    .distinctBy(InetAddress::getHostAddress)
                    .sortedBy(InetAddress::getHostAddress)
            } catch (_: UnknownHostException) {
                return result("dns_failure", null, startedAt)
            } catch (_: Exception) {
                return result("dns_failure", null, startedAt)
            }
        if (addresses.isEmpty()) return result("family_unavailable", null, startedAt)
        var lastResult: TlsHandshakeResult? = null
        var lastAddress: InetAddress? = null
        for (address in addresses.take(MAX_CANDIDATES)) {
            val remainingMillis = timeoutSeconds * MILLIS_PER_SECOND - elapsedMillis(startedAt)
            if (remainingMillis <= 0) return result("deadline_exceeded", lastAddress, lastResult, startedAt)
            val handshake = handshakeProbe.connect(address, host, port, remainingMillis.toInt())
            lastResult = handshake
            lastAddress = address
            if (handshake.status == "trusted") return result("trusted", address, handshake, startedAt)
            if (handshake.status != "connection_failed") return result(handshake.status, address, handshake, startedAt)
        }
        return result(lastResult?.status ?: "connection_failed", lastAddress, lastResult, startedAt)
    }

    private fun selectFamily(
        addresses: List<InetAddress>,
        family: String,
    ): List<InetAddress> =
        addresses.filter {
            when (family) {
                "ipv4" -> it is Inet4Address
                "ipv6" -> it is Inet6Address
                else -> true
            }
        }

    private fun result(
        status: String,
        address: InetAddress?,
        handshake: TlsHandshakeResult?,
        startedAt: Long,
    ) = TlsDiagnosticResult(
        status,
        address?.hostAddress,
        handshake?.tlsProtocol,
        handshake?.cipherSuite,
        handshake?.hostnameVerified,
        handshake?.trustValidated,
        handshake?.certificate,
        elapsedMillis(startedAt),
    )

    private fun result(
        status: String,
        address: InetAddress?,
        startedAt: Long,
    ) = result(status, address, null, startedAt)

    private fun elapsedMillis(startedAt: Long): Long = ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0)

    private companion object {
        const val MAX_CANDIDATES = 8
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** One isolated TLS handshake using JSSE's normal trust and hostname validation. */
fun interface TlsHandshakeProbe {
    /** Connect to a numeric [address] while validating [serverName] against the peer certificate. */
    fun connect(
        address: InetAddress,
        serverName: String,
        port: Int,
        timeoutMillis: Int,
    ): TlsHandshakeResult
}

/** Normalized single-handshake outcome. */
data class TlsHandshakeResult(
    /** Stable status such as `trusted`, `certificate_untrusted`, or `hostname_verification_failed`. */
    val status: String,
    /** Negotiated TLS protocol, if available. */
    val tlsProtocol: String?,
    /** Negotiated cipher suite, if available. */
    val cipherSuite: String?,
    /** Whether hostname verification passed. */
    val hostnameVerified: Boolean?,
    /** Whether the default JVM trust manager accepted the peer chain. */
    val trustValidated: Boolean?,
    /** Public certificate summary, when the peer sent a certificate. */
    val certificate: TlsCertificateSummary?,
)

/** Performs a JSSE handshake with default trust validation and captures public certificate metadata. */
@Component
class JvmTlsHandshakeProbe(
    private val clock: Clock,
    private val serverTrustManagerSupplier: Supplier<X509TrustManager>? = null,
) : TlsHandshakeProbe {
    override fun connect(
        address: InetAddress,
        serverName: String,
        port: Int,
        timeoutMillis: Int,
    ): TlsHandshakeResult {
        val trustManager = runCatching(::recordingTrustManager).getOrElse { return failed("trust_store_unavailable") }
        var session: javax.net.ssl.SSLSession? = null
        val startedAt = System.nanoTime()
        try {
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
            (context.socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.connect(java.net.InetSocketAddress(address, port), timeoutMillis)
                val remainingMillis = timeoutMillis - ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).toInt()
                if (remainingMillis <= 0) return failed("timeout", trustManager)
                socket.soTimeout = remainingMillis
                socket.sslParameters = sslParameters(socket.sslParameters, serverName)
                socket.startHandshake()
                session = socket.session
            }
            val sslSession = session ?: return failed("handshake_failed", trustManager)
            val hostnameVerified = HttpsURLConnection.getDefaultHostnameVerifier().verify(serverName, sslSession)
            val status = if (hostnameVerified) "trusted" else "hostname_verification_failed"
            return result(status, hostnameVerified, true, sslSession.protocol, sslSession.cipherSuite, trustManager)
        } catch (_: SocketTimeoutException) {
            return failed("timeout", trustManager)
        } catch (_: SSLHandshakeException) {
            val status = if (trustManager.trustFailure != null) "certificate_untrusted" else "handshake_failed"
            return failed(status, trustManager)
        } catch (_: SSLException) {
            return failed("handshake_failed", trustManager)
        } catch (_: java.io.IOException) {
            return failed("connection_failed", trustManager)
        } catch (_: IllegalArgumentException) {
            return failed("handshake_failed", trustManager)
        } catch (_: SecurityException) {
            return failed("tls_unavailable", trustManager)
        }
    }

    private fun sslParameters(
        parameters: SSLParameters,
        serverName: String,
    ): SSLParameters =
        parameters.apply {
            if (!serverName.isIpLiteral()) serverNames = listOf(SNIHostName(normalizeNetworkHost(serverName)))
        }

    private fun String.isIpLiteral(): Boolean = ':' in this || IPV4_LITERAL.matches(this)

    private fun recordingTrustManager(): RecordingTrustManager = RecordingTrustManager(serverTrustManagerSupplier.resolveTrustManager())

    private fun failed(
        status: String,
        trustManager: RecordingTrustManager? = null,
        hostnameVerified: Boolean? = null,
    ): TlsHandshakeResult =
        result(
            status,
            hostnameVerified = hostnameVerified,
            trustValidated = trustManager?.let { it.trustFailure == null && it.certificates.isNotEmpty() },
            protocol = null,
            cipher = null,
            trustManager = trustManager,
        )

    private fun result(
        status: String,
        hostnameVerified: Boolean?,
        trustValidated: Boolean?,
        protocol: String?,
        cipher: String?,
        trustManager: RecordingTrustManager?,
    ): TlsHandshakeResult =
        TlsHandshakeResult(status, protocol, cipher, hostnameVerified, trustValidated, trustManager?.certificates?.firstOrNull()?.summary())

    private fun X509Certificate.summary(): TlsCertificateSummary {
        val dnsNames =
            runCatching {
                subjectAlternativeNames
                    .orEmpty()
                    .filter { it.size >= 2 && it[0] == 2 }
                    .mapNotNull { it[1] as? String }
                    .distinct()
                    .take(MAX_DNS_NAMES)
            }.getOrDefault(emptyList())
        val valid =
            runCatching {
                checkValidity(Date.from(clock.instant()))
                true
            }.getOrDefault(false)
        return TlsCertificateSummary(
            subject = subjectX500Principal.name.take(MAX_CERT_FIELD_LENGTH),
            issuer = issuerX500Principal.name.take(MAX_CERT_FIELD_LENGTH),
            notBefore = notBefore.toInstant().toString(),
            notAfter = notAfter.toInstant().toString(),
            validAtCheckTime = valid,
            dnsNames = dnsNames,
        )
    }

    private class RecordingTrustManager(
        private val delegate: X509TrustManager,
    ) : X509TrustManager {
        var certificates: List<X509Certificate> = emptyList()
            private set
        var trustFailure: CertificateException? = null
            private set

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ) = delegate.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ) {
            certificates = chain.toList()
            try {
                delegate.checkServerTrusted(chain, authType)
            } catch (error: CertificateException) {
                trustFailure = error
                throw error
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    private companion object {
        val IPV4_LITERAL = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
        const val MAX_CERT_FIELD_LENGTH = 512
        const val MAX_DNS_NAMES = 16
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
