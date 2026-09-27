package de.heckenmann.visualagent.security

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/** Adds explicitly managed roots to platform trust while preserving standard chain validation. */
internal class CompositeX509TrustManager(
    private val platform: X509TrustManager,
    private val managed: X509TrustManager,
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
    ) = check { manager ->
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
    ) = check { manager ->
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

    private fun check(validation: (X509TrustManager) -> Unit) {
        try {
            validation(platform)
        } catch (platformFailure: CertificateException) {
            try {
                validation(managed)
            } catch (managedFailure: CertificateException) {
                managedFailure.addSuppressed(platformFailure)
                throw managedFailure
            }
        }
    }
}
