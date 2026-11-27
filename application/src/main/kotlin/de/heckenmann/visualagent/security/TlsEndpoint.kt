package de.heckenmann.visualagent.security

import java.net.InetAddress
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.PKIXReason
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

internal data class TlsEndpoint(
    val host: String,
    val port: Int,
) {
    val authority: String get() = if (':' in host) "[$host]:$port" else "$host:$port"
    val alias: String get() = "$ENDPOINT_ALIAS_PREFIX$authority"

    companion object {
        const val ENDPOINT_ALIAS_PREFIX = "endpoint/"

        fun parse(baseUrl: String): TlsEndpoint {
            val url = URI(baseUrl.trim())
            require(url.scheme.equals("https", ignoreCase = true) && url.rawUserInfo == null) { "Use an HTTPS provider URL." }
            return of(requireNotNull(url.host) { "The HTTPS endpoint has no valid host." }, if (url.port == -1) 443 else url.port)
        }

        fun of(
            host: String,
            port: Int,
        ): TlsEndpoint {
            require(host.isNotBlank() && port in 1..65535) { "The HTTPS endpoint has an invalid host or port." }
            val hostname = host.removeSurrounding("[", "]")
            val canonical = if (':' in hostname) InetAddress.getByName(hostname).hostAddress else hostname.trimEnd('.').lowercase()
            return TlsEndpoint(canonical, port)
        }
    }
}

internal fun X509Certificate.sha256Fingerprint(): String = MessageDigest.getInstance("SHA-256").digest(encoded).toHexString()

internal fun Throwable.isMissingTrustAnchor(): Boolean =
    this is CertPathBuilderException || (this is CertPathValidatorException && reason == PKIXReason.NO_TRUST_ANCHOR)

internal fun X509Certificate.checkEndpointValidity() {
    checkValidity()
    val usages = extendedKeyUsage
    if (usages != null && usages.none { it == "1.3.6.1.5.5.7.3.1" || it == "2.5.29.37.0" }) {
        throw CertificateException("The certificate is not permitted for TLS server authentication.")
    }
}

internal fun emptyCertificateStore(): KeyStore = KeyStore.getInstance("PKCS12").apply { load(null) }

internal fun standardTrustManager(store: KeyStore?): X509ExtendedTrustManager =
    TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(store) }
        .trustManagers
        .filterIsInstance<X509ExtendedTrustManager>()
        .first()

internal class EndpointCertificateTrust(
    private val certificate: X509Certificate,
) {
    private val manager by lazy { standardTrustManager(emptyCertificateStore().apply { setCertificateEntry("server", certificate) }) }

    fun check(
        chain: Array<out X509Certificate>,
        validation: (X509ExtendedTrustManager) -> Unit,
    ) {
        if (chain.firstOrNull() != certificate) throw CertificateException("The endpoint certificate changed.")
        certificate.checkEndpointValidity()
        validation(manager)
    }
}
