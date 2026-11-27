package de.heckenmann.visualagent.security

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/** Adds explicitly managed roots to platform trust while preserving standard chain validation. */
internal class CompositeX509TrustManager(
    private val platform: X509TrustManager,
    private val managed: X509TrustManager,
    private val endpointCertificates: Map<String, EndpointCertificateTrust> = emptyMap(),
) : X509ExtendedTrustManager() {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = check { it.checkClientTrusted(chain, authType) }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = check { it.checkServerTrusted(chain, authType) }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
        socket: Socket,
    ) = check { manager ->
        if (manager is X509ExtendedTrustManager) {
            manager.checkClientTrusted(chain, authType, socket)
        } else {
            manager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
        socket: Socket,
    ) = check(
        (socket as? SSLSocket)?.handshakeSession?.let { pin(it.peerHost, it.peerPort) },
        chain,
    ) { manager ->
        if (manager is X509ExtendedTrustManager) {
            manager.checkServerTrusted(chain, authType, socket)
        } else {
            manager.checkServerTrusted(chain, authType)
        }
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) = check { manager ->
        if (manager is X509ExtendedTrustManager) {
            manager.checkClientTrusted(chain, authType, engine)
        } else {
            manager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) = check(pin(engine.peerHost, engine.peerPort), chain) { manager ->
        if (manager is X509ExtendedTrustManager) {
            manager.checkServerTrusted(chain, authType, engine)
        } else {
            manager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        (platform.acceptedIssuers + managed.acceptedIssuers)
            .distinct()
            .toTypedArray()

    private fun pin(
        host: String?,
        port: Int,
    ): EndpointCertificateTrust? =
        if (host.isNullOrBlank() || port !in 1..65535 || endpointCertificates.isEmpty()) {
            null
        } else {
            runCatching { endpointCertificates[TlsEndpoint.of(host, port).alias] }.getOrNull()
        }

    private fun check(
        pin: EndpointCertificateTrust? = null,
        chain: Array<out X509Certificate> = emptyArray(),
        validation: (X509TrustManager) -> Unit,
    ) {
        try {
            checkAuthorities(validation)
        } catch (failure: CertificateException) {
            if (pin == null) throw failure
            // Pin validation uses the same contextual overload and requires the exact reviewed leaf.
            pin.check(chain, validation)
        }
    }

    private fun checkAuthorities(validation: (X509TrustManager) -> Unit) {
        try {
            validation(platform)
        } catch (platformFailure: CertificateException) {
            // Empty PKIX stores cannot validate a chain. Preserve the original platform TLS failure.
            if (managed.acceptedIssuers.isEmpty()) throw platformFailure
            try {
                validation(managed)
            } catch (managedFailure: CertificateException) {
                if (managedFailure.hasMissingTrustAnchor() && !platformFailure.hasMissingTrustAnchor()) {
                    platformFailure.addSuppressed(managedFailure)
                    throw platformFailure
                }
                managedFailure.addSuppressed(platformFailure)
                throw managedFailure
            }
        }
    }

    private fun CertificateException.hasMissingTrustAnchor(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.take(16).any(Throwable::isMissingTrustAnchor)
}
