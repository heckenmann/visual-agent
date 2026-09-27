package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerCertificateInfo
import java.io.ByteArrayInputStream
import java.net.IDN
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.security.auth.x500.X500Principal

/** Validates certificate inputs and exposes only public certificate metadata. */
internal class ServerCertificatePolicy {
    private val certificateFactory = CertificateFactory.getInstance(X509_TYPE)

    /** Validates and normalizes an X.500 distinguished name. */
    fun normalizeSubject(subject: String): String =
        runCatching { X500Principal(subject).name }
            .getOrElse { throw IllegalArgumentException("Certificate subject is not a valid X.500 name.") }

    /** Validates one alias used for a managed certificate entry. */
    fun validateAlias(alias: String) {
        require(alias.matches(ALIAS_PATTERN)) {
            "Alias must use 1-64 letters, digits, dots, underscores, or hyphens and start with a letter or digit."
        }
    }

    /** Parses exactly one ASCII PEM CA certificate and validates its constraints and validity. */
    fun parseCaCertificate(pem: String): X509Certificate {
        require(pem.length <= MAX_CERTIFICATE_CHARS) { "Certificate input exceeds the 128 KiB limit." }
        require(pem.contains(PEM_BEGIN) && pem.all { it.code in ASCII_PRINTABLE_RANGE || it in "\r\n\t" }) {
            "Provide one ASCII PEM-encoded X.509 CA certificate."
        }
        val certificates = certificateFactory.generateCertificates(ByteArrayInputStream(pem.toByteArray(StandardCharsets.US_ASCII)))
        require(certificates.size == 1) { "Provide exactly one X.509 CA certificate." }
        val certificate = certificates.single() as? X509Certificate ?: throw IllegalArgumentException("Certificate must use X.509 format.")
        require(certificate.basicConstraints >= 0) { "Only CA certificates can be added to the trust store." }
        validateCertificateSigningUsage(certificate, "The CA certificate")
        validateCertificateValidity(certificate, "The CA certificate")
        return certificate
    }

    /** Validates and canonicalizes an exact DNS subject alternative name. */
    fun normalizeDnsName(name: String): String {
        require(name.length in 1..MAX_DNS_NAME_CHARS && !name.contains('*')) { "DNS SAN must be a valid exact host name." }
        val ascii =
            runCatching { IDN.toASCII(name.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase() }
                .getOrElse { throw IllegalArgumentException("DNS SAN is invalid.") }
        require(ascii.length <= MAX_DNS_NAME_CHARS && ascii.split('.').all { it.isNotEmpty() && it.length <= MAX_DNS_LABEL_CHARS }) {
            "DNS SAN is invalid."
        }
        return ascii
    }

    /** Validates and canonicalizes a numeric IPv4 or IPv6 subject alternative name. */
    fun normalizeIpAddress(address: String): String {
        require(address.isNotBlank() && '%' !in address) { "IP SAN must be a numeric address without a zone identifier." }
        val parts = address.split('.')
        if (parts.size == IPV4_OCTETS) {
            require(parts.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }) { "IP SAN is invalid." }
            return parts.joinToString(".") { it.toInt().toString() }
        }
        require(':' in address) { "IP SAN must be a numeric IPv4 or IPv6 address." }
        val parsed = runCatching { InetAddress.getByName(address) }.getOrElse { throw IllegalArgumentException("IP SAN is invalid.") }
        require(parsed.address.size == IPV6_BYTES) { "IP SAN must be a numeric IPv6 address." }
        return parsed.hostAddress
    }

    /** Creates safe metadata for a public X.509 certificate. */
    fun certificateInfo(
        certificate: X509Certificate,
        includePem: Boolean,
    ): ServerCertificateInfo =
        ServerCertificateInfo(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            sha256 = MessageDigest.getInstance(SHA_256).digest(certificate.encoded).joinToString("") { "%02x".format(it) },
            notBefore = DateTimeFormatter.ISO_INSTANT.format(certificate.notBefore.toInstant()),
            notAfter = DateTimeFormatter.ISO_INSTANT.format(certificate.notAfter.toInstant()),
            basicConstraints = certificate.basicConstraints,
            subjectAlternativeNames = certificate.subjectAlternativeNames.orEmpty().mapNotNull(::formatSubjectAlternativeName),
            pem = if (includePem) certificate.toPem() else null,
        )

    /** Validates the signing key usage when a certificate extension is present. */
    fun validateCertificateSigningUsage(
        certificate: X509Certificate,
        description: String,
    ) {
        val usage = certificate.keyUsage
        require(usage == null || (usage.size > KEY_CERT_SIGN_USAGE_INDEX && usage[KEY_CERT_SIGN_USAGE_INDEX])) {
            "$description is not permitted to sign certificates."
        }
    }

    /** Ensures that a certificate is currently within its validity interval. */
    fun validateCertificateValidity(
        certificate: X509Certificate,
        description: String,
    ) {
        runCatching { certificate.checkValidity() }
            .getOrElse { throw IllegalArgumentException("$description is not currently valid.") }
    }

    private fun formatSubjectAlternativeName(name: List<*>): String? {
        if (name.size < SAN_PAIR_SIZE) return null
        val type = name[0] as? Int ?: return null
        val value = name[1]?.toString() ?: return null
        return when (type) {
            SAN_DNS -> "dns:$value"
            SAN_IP -> "ip:$value"
            else -> null
        }
    }

    private fun X509Certificate.toPem(): String {
        val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, NEWLINE_BYTES).encodeToString(encoded)
        return "$PEM_BEGIN\n$body\n$PEM_END\n"
    }

    private companion object {
        val ALIAS_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        const val X509_TYPE = "X.509"
        const val SHA_256 = "SHA-256"
        const val MAX_CERTIFICATE_CHARS = 131_072
        val ASCII_PRINTABLE_RANGE = 0x20..0x7e
        const val MAX_DNS_NAME_CHARS = 253
        const val MAX_DNS_LABEL_CHARS = 63
        const val IPV4_OCTETS = 4
        const val IPV6_BYTES = 16
        const val SAN_PAIR_SIZE = 2
        const val SAN_DNS = 2
        const val SAN_IP = 7
        const val KEY_CERT_SIGN_USAGE_INDEX = 5
        const val PEM_LINE_LENGTH = 64
        const val PEM_BEGIN = "-----BEGIN CERTIFICATE-----"
        const val PEM_END = "-----END CERTIFICATE-----"
        val NEWLINE_BYTES = "\n".toByteArray(StandardCharsets.US_ASCII)
    }
}
