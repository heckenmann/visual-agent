package de.heckenmann.visualagent.protocol

/** Maximum size of one public PEM certificate accepted by the managed trust store. */
const val MAX_TRUST_CERTIFICATE_PEM_BYTES = 128 * 1024

private const val CERTIFICATE_BEGIN = "-----BEGIN CERTIFICATE-----"
private const val CERTIFICATE_END = "-----END CERTIFICATE-----"

/** Checks the envelope before transferring a file. Only the server validates X.509 and CA constraints. */
fun isSinglePublicCertificatePem(input: String): Boolean {
    if (input.length > MAX_TRUST_CERTIFICATE_PEM_BYTES || input.any { it.code !in 0x20..0x7e && it !in "\r\n\t" }) return false
    val pem = input.trim()
    return pem.startsWith(CERTIFICATE_BEGIN) &&
        pem.endsWith(CERTIFICATE_END) &&
        '-' !in pem.removePrefix(CERTIFICATE_BEGIN).removeSuffix(CERTIFICATE_END)
}

/** Public identity of a certificate stored on the connected Visual Agent server. */
data class TrustedCertificate(
    val alias: String,
    val subject: String,
    val issuer: String,
    val notBefore: String,
    val notAfter: String,
    val sha256: String,
    val endpoint: String? = null,
)

/** Role of a public certificate in a provider's presented TLS chain. */
enum class PresentedCertificateKind {
    SERVER,
    INTERMEDIATE_CA,
    ROOT_CA,
}

/** Certificate metadata for explicit trust review. */
data class PresentedCertificate(
    val certificate: TrustedCertificate,
    val kind: PresentedCertificateKind,
    val canImport: Boolean,
)

/** Public certificates presented by the named HTTPS endpoint. */
data class PresentedCertificateChain(
    val endpoint: String,
    val certificates: List<PresentedCertificate>,
)

/** Administrative access to the connected server's additive certificate trust store. */
interface CertificateTrustPort {
    /** Lists public metadata for the managed trust anchors. */
    fun list(): List<TrustedCertificate>

    /** Imports one public X.509 CA certificate encoded as PEM. */
    fun importCa(
        alias: String,
        pem: String,
    ): TrustedCertificate

    /** Exports the public PEM for a managed trust alias, if present. */
    fun exportCertificate(alias: String): String?

    /** Removes the confirmed certificate only if its fingerprint still matches. */
    fun removeCertificate(
        alias: String,
        sha256: String,
    ): Boolean

    /** Reads the public chain without sending provider credentials. */
    fun reviewEndpoint(baseUrl: String): PresentedCertificateChain

    /** Imports the selected entries only while the endpoint still presents the reviewed chain. */
    fun importEndpointCertificates(
        baseUrl: String,
        reviewedFingerprints: List<String>,
        selectedFingerprints: Set<String>,
    )
}
