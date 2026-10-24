package de.heckenmann.visualagent.agent.provider

import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Supplies server-owned trust material to outbound TLS clients without weakening normal certificate checks. */
fun interface ServerTrustManagerProvider {
    /** Returns an X.509 trust manager combining platform roots with application-managed additional roots. */
    fun trustManager(): X509TrustManager

    /** Supplies a consistent policy snapshot; null revision deliberately disables transport reuse. */
    fun trustSnapshot(): ServerTrustSnapshot = ServerTrustSnapshot(trustManager())
}

/** Trust policy and its provider-owned revision, which must change whenever validation policy changes. */
data class ServerTrustSnapshot(
    val manager: X509TrustManager,
    val revision: String? = null,
)

/** Returns the configured managed trust manager, or JSSE's platform default when none is wired. */
fun ServerTrustManagerProvider?.resolveTrustManager(): X509TrustManager {
    if (this != null) return trustManager()
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/** Creates a JSSE context with the same platform and managed roots used by provider transports. */
fun ServerTrustManagerProvider?.resolveSslContext(): SSLContext {
    val manager = resolveTrustManager()
    return SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
}
