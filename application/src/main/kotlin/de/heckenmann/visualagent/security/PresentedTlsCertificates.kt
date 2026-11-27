package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.HostResolver
import de.heckenmann.visualagent.agent.tools.JvmHostResolver
import de.heckenmann.visualagent.protocol.PresentedCertificate
import de.heckenmann.visualagent.protocol.PresentedCertificateChain
import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import de.heckenmann.visualagent.protocol.TrustedCertificate
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.GeneralSecurityException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Reads the certificates offered by a provider with a TLS-only connection. */
@Component
internal class PresentedTlsCertificates(
    private val resolver: HostResolver = JvmHostResolver(),
) {
    private val policy = ServerCertificatePolicy()

    fun review(endpoint: TlsEndpoint): PresentedCertificateChain =
        PresentedCertificateChain(
            endpoint.authority,
            readChain(endpoint).map { (certificate, kind) ->
                val info = policy.certificateInfo(certificate, includePem = false)
                PresentedCertificate(
                    TrustedCertificate(info.sha256, info.subject, info.issuer, info.notBefore, info.notAfter, info.sha256),
                    kind,
                    canImport(certificate, kind),
                )
            },
        )

    fun confirm(
        endpoint: TlsEndpoint,
        reviewed: List<String>,
        selected: Set<String>,
    ): List<Pair<X509Certificate, PresentedCertificateKind>> {
        require(reviewed.size in 1..MAX_CHAIN_LENGTH && selected.isNotEmpty() && reviewed.containsAll(selected)) {
            "Select at least one of the reviewed certificates."
        }
        val chain = readChain(endpoint)
        require(chain.map { it.first.sha256Fingerprint() } == reviewed) {
            "The endpoint presented a different certificate chain. Review it again."
        }
        val approved = chain.filter { (certificate, _) -> certificate.sha256Fingerprint() in selected }
        require(approved.all { (certificate, kind) -> canImport(certificate, kind) }) { "Choose currently valid certificates." }
        val leaf = chain.first().first
        leaf.checkEndpointValidity()
        // JSSE verifies the hostname and completes proof of possession before anything is persisted.
        val anchors = emptyCertificateStore()
        approved.filter { it.second != PresentedCertificateKind.SERVER }.forEach { (certificate, _) ->
            anchors.setCertificateEntry(certificate.sha256Fingerprint(), certificate)
        }
        val caTrust = standardTrustManager(anchors)
        val verification =
            if (approved.any { it.second == PresentedCertificateKind.SERVER }) {
                CompositeX509TrustManager(
                    if (anchors.size() > 0) caTrust else standardTrustManager(null),
                    standardTrustManager(emptyCertificateStore()),
                    mapOf(endpoint.alias to EndpointCertificateTrust(leaf)),
                )
            } else {
                caTrust
            }
        connect(endpoint, verification, verifyHostname = true) { socket ->
            val peer = socket.session.peerCertificates.filterIsInstance<X509Certificate>()
            require(linkedChain(peer).map { it.first.sha256Fingerprint() } == reviewed) {
                "The endpoint presented a different certificate chain. Review it again."
            }
        }
        return approved
    }

    private fun readChain(endpoint: TlsEndpoint): List<Pair<X509Certificate, PresentedCertificateKind>> {
        val capture = RejectingCertificateCapture()
        try {
            connect(endpoint, capture, verifyHostname = false) {}
        } catch (failure: SSLHandshakeException) {
            if (capture.certificates.isEmpty()) throw failure
        }
        require(capture.certificates.size in 1..MAX_CHAIN_LENGTH) { "The endpoint did not present a supported certificate chain." }
        return linkedChain(capture.certificates)
    }

    private fun connect(
        endpoint: TlsEndpoint,
        trustManager: X509TrustManager,
        verifyHostname: Boolean,
        onConnected: (SSLSocket) -> Unit,
    ) {
        val context = SSLContext.getInstance("TLS").apply { init(emptyArray(), arrayOf(trustManager), null) }
        val addresses = resolver.resolve(endpoint.host, null).distinct()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS.toLong())
        connectTransport(endpoint, addresses, deadline).use { transport ->
            (context.socketFactory.createSocket(transport, endpoint.host, endpoint.port, true) as SSLSocket).use { socket ->
                socket.soTimeout = remainingMillis(deadline)
                if (verifyHostname) socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                socket.startHandshake()
                onConnected(socket)
            }
        }
    }

    private fun connectTransport(
        endpoint: TlsEndpoint,
        addresses: List<InetAddress>,
        deadline: Long,
    ): Socket {
        var lastFailure: IOException? = null
        for ((index, address) in addresses.withIndex()) {
            val timeout = (remainingMillis(deadline) / (addresses.size - index)).coerceAtLeast(1)
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, endpoint.port), timeout)
                return socket
            } catch (failure: IOException) {
                lastFailure = failure
                socket.close()
            }
        }
        throw lastFailure ?: IOException("The HTTPS endpoint did not resolve to an address.")
    }

    private fun remainingMillis(deadline: Long): Int {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw SocketTimeoutException("TLS connection timed out.")
        return TimeUnit.NANOSECONDS
            .toMillis(remaining)
            .toInt()
            .coerceAtLeast(1)
    }

    private fun linkedChain(presented: List<X509Certificate>): List<Pair<X509Certificate, PresentedCertificateKind>> =
        buildList {
            var child = presented.first()
            val issuers = presented.drop(1).toMutableList()
            add(child to PresentedCertificateKind.SERVER)
            while (true) {
                val issuer = issuers.firstOrNull { it.basicConstraints >= 0 && it != child && child.isSignedBy(it) } ?: break
                issuers.removeAll { it == issuer }
                val root = issuer.isSignedBy(issuer)
                add(issuer to if (root) PresentedCertificateKind.ROOT_CA else PresentedCertificateKind.INTERMEDIATE_CA)
                if (root) break
                child = issuer
            }
        }

    private fun X509Certificate.isSignedBy(issuer: X509Certificate): Boolean {
        if (issuerX500Principal != issuer.subjectX500Principal) return false
        return try {
            verify(issuer.publicKey)
            true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    private fun canImport(
        certificate: X509Certificate,
        kind: PresentedCertificateKind,
    ): Boolean =
        runCatching {
            if (kind == PresentedCertificateKind.SERVER) {
                certificate.checkEndpointValidity()
            } else {
                certificate.checkValidity()
                policy.validateCertificateSigningUsage(certificate, "The CA certificate")
            }
        }.isSuccess

    private class RejectingCertificateCapture : X509TrustManager {
        var certificates: List<X509Certificate> = emptyList()
            private set

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ): Unit = throw CertificateException("Client authentication is unavailable during certificate review.")

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ) {
            certificates = chain.toList()
            throw CertificateException("Certificate review ends before establishing a trusted session.")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 4_000
        const val MAX_CHAIN_LENGTH = 10
    }
}
