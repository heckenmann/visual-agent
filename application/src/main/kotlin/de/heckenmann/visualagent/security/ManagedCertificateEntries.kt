package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.SslBundleKey
import org.springframework.boot.ssl.SslManagerBundle
import org.springframework.boot.ssl.SslOptions
import org.springframework.boot.ssl.SslStoreBundle
import java.security.KeyStore
import java.security.cert.X509Certificate

internal fun String.isEndpointCertificateAlias(): Boolean = startsWith(TlsEndpoint.ENDPOINT_ALIAS_PREFIX)

internal fun KeyStore.endpointCertificateEntries(): Map<String, X509Certificate> =
    aliases()
        .toList()
        .filter(String::isEndpointCertificateAlias)
        .associateWith { getCertificate(it) as X509Certificate }

internal fun KeyStore.caCertificateStore(): KeyStore =
    emptyCertificateStore().apply {
        val persisted = this@caCertificateStore
        persisted.aliases().toList().filterNot(String::isEndpointCertificateAlias).forEach { alias ->
            setCertificateEntry(alias, persisted.getCertificate(alias))
        }
    }

internal fun buildTrustBundle(
    roots: KeyStore,
    pins: Map<String, X509Certificate>,
): SslBundle =
    SslBundle.of(
        SslStoreBundle.of(null, null, roots),
        SslBundleKey.NONE,
        SslOptions.NONE,
        "TLS",
        SslManagerBundle.from(
            CompositeX509TrustManager(
                standardTrustManager(null),
                standardTrustManager(roots),
                pins.mapValues { EndpointCertificateTrust(it.value) },
            ),
        ),
    )

/** Applies the explicitly approved certificates to a single PKCS#12 transaction. */
internal fun KeyStore.addReviewedCertificates(
    endpoint: TlsEndpoint,
    approved: List<Pair<X509Certificate, PresentedCertificateKind>>,
) {
    approved.forEach { (certificate, kind) ->
        if (kind == PresentedCertificateKind.SERVER) {
            setCertificateEntry(endpoint.alias, certificate)
        } else if (getCertificateAlias(certificate)?.let { !it.isEndpointCertificateAlias() } != true) {
            val alias = certificate.sha256Fingerprint()
            require(!containsAlias(alias) || getCertificate(alias) == certificate) {
                "A managed CA already uses the fingerprint as its alias. Choose another alias in Settings."
            }
            setCertificateEntry(alias, certificate)
        }
    }
}
