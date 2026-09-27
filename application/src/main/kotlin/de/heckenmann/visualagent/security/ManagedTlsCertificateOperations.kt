package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.GeneratedServerCertificate
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import java.security.cert.X509Certificate

/** Generates and validates managed TLS key-store entries. */
@Component
internal class ManagedTlsCertificateOperations(
    private val files: ManagedTlsStoreFiles,
    private val mutationAudit: TlsMutationAudit,
) {
    private val keytool = KeytoolCertificateGenerator()
    private val certificatePolicy = ServerCertificatePolicy()

    /** Generates a bounded certificate and persists it only after validating the resulting key store. */
    fun generate(
        alias: String,
        subject: String,
        dnsNames: List<String>,
        ipAddresses: List<String>,
        certificateAuthority: Boolean,
        validityDays: Int,
        signingCaAlias: String?,
    ): GeneratedServerCertificate =
        mutationAudit.record(
            "generate_certificate",
            ServerTlsStore.KEY,
            alias,
            { certificateFingerprint(alias) },
        ) {
            certificatePolicy.validateAlias(alias)
            require(subject.length in 1..MAX_SUBJECT_CHARS) {
                "Certificate subject must be between 1 and $MAX_SUBJECT_CHARS characters."
            }
            val canonicalSubject = certificatePolicy.normalizeSubject(subject)
            require(validityDays in MIN_VALIDITY_DAYS..MAX_VALIDITY_DAYS) {
                "Certificate validity must be between 1 and $MAX_VALIDITY_DAYS days."
            }
            require(dnsNames.size <= MAX_SANS && ipAddresses.size <= MAX_SANS) {
                "At most $MAX_SANS DNS names and IP addresses are allowed."
            }
            val normalizedDnsNames = dnsNames.map(certificatePolicy::normalizeDnsName).distinct()
            val normalizedIps = ipAddresses.map(certificatePolicy::normalizeIpAddress).distinct()
            val keyStore = files.load(ServerTlsStore.KEY)
            validateSigningConfiguration(
                keyStore,
                certificateAuthority,
                signingCaAlias,
                normalizedDnsNames,
                normalizedIps,
            )
            require(!keyStore.containsAlias(alias)) { "A key-store entry already exists for that alias." }
            val temporaryStore = Files.createTempFile(files.directory, TEMP_PREFIX, PKCS12_SUFFIX)
            try {
                Files.copy(files.storePath(ServerTlsStore.KEY), temporaryStore, REPLACE_EXISTING)
                keytool.generate(
                    CertificateGenerationRequest(
                        alias = alias,
                        subject = canonicalSubject,
                        dnsNames = normalizedDnsNames,
                        ipAddresses = normalizedIps,
                        certificateAuthority = certificateAuthority,
                        validityDays = validityDays,
                        signingCaAlias = signingCaAlias,
                        store = temporaryStore,
                        passwordFile = files.passwordFile(ServerTlsStore.KEY),
                    ),
                )
                val generatedStore = loadTemporaryKeyStore(temporaryStore)
                val certificate = validateGeneratedEntry(generatedStore, alias, certificateAuthority, signingCaAlias)
                files.persist(ServerTlsStore.KEY, generatedStore)
                GeneratedServerCertificate(
                    alias = alias,
                    certificate = certificatePolicy.certificateInfo(certificate, includePem = false),
                    certificateAuthority = certificateAuthority,
                    restartRequired = true,
                )
            } finally {
                Files.deleteIfExists(temporaryStore)
            }
        }

    private fun validateSigningConfiguration(
        keyStore: KeyStore,
        certificateAuthority: Boolean,
        signingCaAlias: String?,
        dnsNames: List<String>,
        ipAddresses: List<String>,
    ) {
        if (certificateAuthority) {
            require(signingCaAlias == null) { "A CA certificate must be self-signed and cannot use another signing alias." }
            require(dnsNames.isEmpty() && ipAddresses.isEmpty()) {
                "CA certificates cannot include server subject alternative names."
            }
            return
        }
        require(dnsNames.isNotEmpty() || ipAddresses.isNotEmpty()) {
            "Server certificates require at least one DNS or IP subject alternative name."
        }
        require(!signingCaAlias.isNullOrBlank()) { "Server certificates must be signed by a managed CA key alias." }
        certificatePolicy.validateAlias(signingCaAlias)
        val signingCertificate = keyStore.getCertificate(signingCaAlias) as? X509Certificate
        require(keyStore.isKeyEntry(signingCaAlias) && signingCertificate?.basicConstraints?.let { it >= 0 } == true) {
            "The signing alias must contain a private key and CA certificate in the managed key store."
        }
        certificatePolicy.validateCertificateValidity(checkNotNull(signingCertificate), "The signing CA certificate")
        certificatePolicy.validateCertificateSigningUsage(checkNotNull(signingCertificate), "The selected CA certificate")
    }

    private fun validateGeneratedEntry(
        generatedStore: KeyStore,
        alias: String,
        certificateAuthority: Boolean,
        signingCaAlias: String?,
    ): X509Certificate {
        require(generatedStore.isKeyEntry(alias)) { "Generated material did not contain a private key entry." }
        val certificate =
            generatedStore.getCertificate(alias) as? X509Certificate
                ?: throw IllegalStateException("Generated certificate is unavailable.")
        require((certificate.basicConstraints >= 0) == certificateAuthority) {
            "Generated certificate constraints did not match the request."
        }
        if (!certificateAuthority) {
            val signer = checkNotNull(signingCaAlias)
            require(generatedStore.isKeyEntry(signer)) { "The signing alias does not contain a private CA key." }
            val issuer = generatedStore.getCertificate(signer) as? X509Certificate
            require(
                issuer != null &&
                    issuer.basicConstraints >= 0 &&
                    certificate.issuerX500Principal == issuer.subjectX500Principal,
            ) {
                "Generated server certificate was not signed by the selected managed CA."
            }
            certificate.verify(issuer.publicKey)
            require(certificate.subjectAlternativeNames.orEmpty().isNotEmpty()) {
                "Generated server certificate is missing subject alternative names."
            }
        }
        return certificate
    }

    private fun certificateFingerprint(alias: String): String? {
        certificatePolicy.validateAlias(alias)
        val certificate = files.load(ServerTlsStore.KEY).getCertificate(alias) as? X509Certificate ?: return null
        return certificatePolicy.certificateInfo(certificate, includePem = false).sha256
    }

    private fun loadTemporaryKeyStore(path: Path): KeyStore {
        val keyStore = KeyStore.getInstance(PKCS12_TYPE)
        val password = files.passwordChars(ServerTlsStore.KEY)
        try {
            Files.newInputStream(path).use { keyStore.load(it, password) }
        } finally {
            password.fill('\u0000')
        }
        return keyStore
    }

    private companion object {
        const val MAX_SUBJECT_CHARS = 512
        const val MAX_SANS = 20
        const val MIN_VALIDITY_DAYS = 1
        const val MAX_VALIDITY_DAYS = 825
        const val TEMP_PREFIX = ".generated-keystore-"
        const val PKCS12_SUFFIX = ".p12"
        const val PKCS12_TYPE = "PKCS12"
    }
}
