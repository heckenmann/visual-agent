package de.heckenmann.visualagent.agent.tools

/** Identifies one application-owned server TLS store. */
enum class ServerTlsStore {
    /** Additional server-side trust anchors; platform roots are not modified. */
    TRUST,

    /** Private keys and their corresponding public certificate chains. */
    KEY,
}

/** Public certificate metadata safe to return to an agent. */
data class ServerCertificateInfo(
    /** X.509 subject distinguished name. */
    val subject: String,
    /** X.509 issuer distinguished name. */
    val issuer: String,
    /** SHA-256 certificate fingerprint in lowercase hexadecimal. */
    val sha256: String,
    /** Certificate validity start as ISO-8601 UTC. */
    val notBefore: String,
    /** Certificate validity end as ISO-8601 UTC. */
    val notAfter: String,
    /** Basic constraints value, or -1 when the certificate is not a CA. */
    val basicConstraints: Int,
    /** DNS and IP subject alternative names. */
    val subjectAlternativeNames: List<String>,
    /** PEM-encoded public certificate, when explicitly requested. */
    val pem: String? = null,
)

/** One public view of an application-owned keystore or truststore entry. */
data class ServerTlsEntry(
    /** Stable store alias. */
    val alias: String,
    /** `trusted_certificate` or `private_key`; never includes secret key bytes. */
    val entryType: String,
    /** Public certificates associated with the entry, leaf first. */
    val certificates: List<ServerCertificateInfo>,
)

/** Result of a key-pair and certificate generation operation. */
data class GeneratedServerCertificate(
    /** Generated alias. */
    val alias: String,
    /** Generated leaf certificate metadata. */
    val certificate: ServerCertificateInfo,
    /** Whether the certificate is a CA certificate. */
    val certificateAuthority: Boolean,
    /** Whether the running TLS consumers need restart to load the material. */
    val restartRequired: Boolean,
)

/** Server-owned trust/key-store operations exposed to narrow model tools. */
interface ServerTlsMaterialPort {
    /** Lists public entry metadata for the selected managed store. */
    fun list(store: ServerTlsStore): List<ServerTlsEntry>

    /** Returns one entry by exact alias, optionally including only its public certificate PEM. */
    fun inspect(
        store: ServerTlsStore,
        alias: String,
        includePem: Boolean,
    ): ServerTlsEntry?

    /** Adds one validated CA certificate to the managed additive trust store. */
    fun importTrustedCertificate(
        alias: String,
        certificate: String,
    ): ServerTlsEntry

    /** Removes one exact alias from the managed trust store. */
    fun removeTrustedCertificate(alias: String): Boolean

    /** Generates a constrained self-signed CA or TLS server key pair and certificate. */
    fun generateCertificate(
        alias: String,
        subject: String,
        dnsNames: List<String>,
        ipAddresses: List<String>,
        certificateAuthority: Boolean,
        validityDays: Int,
        signingCaAlias: String? = null,
    ): GeneratedServerCertificate

    /** Removes one exact entry from the managed key store. */
    fun removeKeyEntry(alias: String): Boolean

    /** Returns a public PEM certificate only; private key encoding is never available. */
    fun exportPublicCertificate(alias: String): String?
}
