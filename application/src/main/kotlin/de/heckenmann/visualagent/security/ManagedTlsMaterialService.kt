package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.tools.GeneratedServerCertificate
import de.heckenmann.visualagent.agent.tools.ServerTlsEntry
import de.heckenmann.visualagent.agent.tools.ServerTlsMaterialPort
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.SslBundleKey
import org.springframework.boot.ssl.SslManagerBundle
import org.springframework.boot.ssl.SslOptions
import org.springframework.boot.ssl.SslStoreBundle
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.function.Supplier
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Owns the managed TLS stores below the server data root and exposes secret-safe operations. */
@Component
class ManagedTlsMaterialService(
    @Qualifier("serverDataRoot") serverDataRoot: Path,
) : ServerTlsMaterialPort,
    ServerTrustManagerProvider,
    Supplier<X509TrustManager> {
    private val files = ManagedTlsStoreFiles(serverDataRoot)
    private val keytool = KeytoolCertificateGenerator()
    private val certificatePolicy = ServerCertificatePolicy()
    private val keyStoreSelector = ManagedTlsKeyStoreSelector()

    /** Builds a trust manager that accepts platform roots and explicitly imported managed CA certificates. */
    @Synchronized
    override fun trustManager(): X509TrustManager =
        trustBundle()
            .managers.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    /** Supplies managed trust to JDK-only tools without coupling their module to provider APIs. */
    override fun get(): X509TrustManager = trustManager()

    /** Creates the Spring SSL bundle used by outbound TLS clients. */
    @Synchronized
    fun trustBundle(): SslBundle {
        val platformFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        platformFactory.init(null as KeyStore?)
        val managedFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        managedFactory.init(files.load(ServerTlsStore.TRUST))
        val platform = platformFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
        val managed = managedFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
        val stores = SslStoreBundle.of(null, null, files.load(ServerTlsStore.TRUST))
        return SslBundle.of(
            stores,
            SslBundleKey.NONE,
            SslOptions.NONE,
            TLS_PROTOCOL,
            SslManagerBundle.from(CompositeX509TrustManager(platform, managed)),
        )
    }

    /** Creates the JDK key manager for an exact managed private-key alias. */
    @Synchronized
    fun keyManagerFactory(alias: String): javax.net.ssl.KeyManagerFactory = keyBundle(alias).managers.keyManagerFactory

    /** Creates a Spring SSL bundle for an exact managed server certificate alias. */
    @Synchronized
    fun keyBundle(alias: String): SslBundle {
        certificatePolicy.validateAlias(alias)
        val keyStore = files.load(ServerTlsStore.KEY)
        require(keyStore.isKeyEntry(alias)) { "No private key exists for that key-store alias." }
        val password = files.passwordChars(ServerTlsStore.KEY)
        try {
            val selectedKeyStore = keyStoreSelector.select(keyStore, alias, password)
            val keyManagerFactory =
                javax.net.ssl.KeyManagerFactory
                    .getInstance(
                        javax.net.ssl.KeyManagerFactory
                            .getDefaultAlgorithm(),
                    ).apply { init(selectedKeyStore, password) }
            val trustBundle = trustBundle()
            val stores = SslStoreBundle.of(selectedKeyStore, String(password), files.load(ServerTlsStore.TRUST))
            return SslBundle.of(
                stores,
                SslBundleKey.of(alias, String(password)),
                SslOptions.NONE,
                TLS_PROTOCOL,
                SslManagerBundle.of(keyManagerFactory, trustBundle.managers.trustManagerFactory),
            )
        } finally {
            password.fill('\u0000')
        }
    }

    /** Lists aliases and public certificates from one managed store, initializing it when needed. */
    @Synchronized
    override fun list(store: ServerTlsStore): List<ServerTlsEntry> =
        files
            .load(store)
            .aliases()
            .toList()
            .sorted()
            .mapNotNull { entry(store, it, includePem = false) }

    /** Inspects one exact alias and optionally returns its public PEM certificate. */
    @Synchronized
    override fun inspect(
        store: ServerTlsStore,
        alias: String,
        includePem: Boolean,
    ): ServerTlsEntry? {
        certificatePolicy.validateAlias(alias)
        return entry(store, alias, includePem)
    }

    /** Imports exactly one currently valid CA certificate into the additive managed trust store. */
    @Synchronized
    override fun importTrustedCertificate(
        alias: String,
        certificate: String,
    ): ServerTlsEntry {
        certificatePolicy.validateAlias(alias)
        val trusted = certificatePolicy.parseCaCertificate(certificate)
        val keyStore = files.load(ServerTlsStore.TRUST)
        require(!keyStore.containsAlias(alias)) { "A trust-store entry already exists for that alias." }
        keyStore.setCertificateEntry(alias, trusted)
        files.persist(ServerTlsStore.TRUST, keyStore)
        return checkNotNull(entry(ServerTlsStore.TRUST, alias, includePem = false))
    }

    /** Removes one exact public certificate alias from the managed trust store. */
    @Synchronized
    override fun removeTrustedCertificate(alias: String): Boolean {
        certificatePolicy.validateAlias(alias)
        val keyStore = files.load(ServerTlsStore.TRUST)
        if (!keyStore.isCertificateEntry(alias)) return false
        keyStore.deleteEntry(alias)
        files.persist(ServerTlsStore.TRUST, keyStore)
        return true
    }

    /** Generates a bounded RSA-3072 self-signed certificate and validates the result before saving it. */
    @Synchronized
    override fun generateCertificate(
        alias: String,
        subject: String,
        dnsNames: List<String>,
        ipAddresses: List<String>,
        certificateAuthority: Boolean,
        validityDays: Int,
        signingCaAlias: String?,
    ): GeneratedServerCertificate {
        certificatePolicy.validateAlias(alias)
        require(subject.length in 1..MAX_SUBJECT_CHARS) { "Certificate subject must be between 1 and $MAX_SUBJECT_CHARS characters." }
        val canonicalSubject = certificatePolicy.normalizeSubject(subject)
        require(
            validityDays in MIN_VALIDITY_DAYS..MAX_VALIDITY_DAYS,
        ) { "Certificate validity must be between 1 and $MAX_VALIDITY_DAYS days." }
        require(dnsNames.size <= MAX_SANS && ipAddresses.size <= MAX_SANS) { "At most $MAX_SANS DNS names and IP addresses are allowed." }
        val normalizedDnsNames = dnsNames.map(certificatePolicy::normalizeDnsName).distinct()
        val normalizedIps = ipAddresses.map(certificatePolicy::normalizeIpAddress).distinct()
        val keyStore = files.load(ServerTlsStore.KEY)
        if (certificateAuthority) {
            require(signingCaAlias == null) { "A CA certificate must be self-signed and cannot use another signing alias." }
            require(
                normalizedDnsNames.isEmpty() && normalizedIps.isEmpty(),
            ) { "CA certificates cannot include server subject alternative names." }
        } else {
            require(normalizedDnsNames.isNotEmpty() || normalizedIps.isNotEmpty()) {
                "Server certificates require at least one DNS or IP subject alternative name."
            }
            require(!signingCaAlias.isNullOrBlank()) { "Server certificates must be signed by a managed CA key alias." }
            certificatePolicy.validateAlias(signingCaAlias)
            val signingCertificate = keyStore.getCertificate(signingCaAlias) as? X509Certificate
            require(keyStore.isKeyEntry(signingCaAlias) && signingCertificate?.basicConstraints?.let { it >= 0 } == true) {
                "The signing alias must contain a private key and CA certificate in the managed key store."
            }
            val validatedSigner = checkNotNull(signingCertificate)
            certificatePolicy.validateCertificateValidity(validatedSigner, "The signing CA certificate")
            certificatePolicy.validateCertificateSigningUsage(validatedSigner, "The selected CA certificate")
        }

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
            require(generatedStore.isKeyEntry(alias)) { "Generated material did not contain a private key entry." }
            val certificate =
                generatedStore.getCertificate(alias) as? X509Certificate
                    ?: throw IllegalStateException("Generated certificate is unavailable.")
            require(
                (certificate.basicConstraints >= 0) == certificateAuthority,
            ) { "Generated certificate constraints did not match the request." }
            if (!certificateAuthority) {
                val signer = checkNotNull(signingCaAlias)
                require(generatedStore.isKeyEntry(signer)) { "The signing alias does not contain a private CA key." }
                val issuer = generatedStore.getCertificate(signer) as? X509Certificate
                require(issuer != null && issuer.basicConstraints >= 0 && certificate.issuerX500Principal == issuer.subjectX500Principal) {
                    "Generated server certificate was not signed by the selected managed CA."
                }
                certificate.verify(issuer.publicKey)
            }
            if (!certificateAuthority) {
                require(certificate.subjectAlternativeNames.orEmpty().isNotEmpty()) {
                    "Generated server certificate is missing subject alternative names."
                }
            }
            files.persist(ServerTlsStore.KEY, generatedStore)
            return GeneratedServerCertificate(
                alias = alias,
                certificate = certificatePolicy.certificateInfo(certificate, includePem = false),
                certificateAuthority = certificateAuthority,
                restartRequired = true,
            )
        } finally {
            Files.deleteIfExists(temporaryStore)
        }
    }

    /** Removes one exact alias from the managed private key store. */
    @Synchronized
    override fun removeKeyEntry(alias: String): Boolean {
        certificatePolicy.validateAlias(alias)
        val keyStore = files.load(ServerTlsStore.KEY)
        if (!keyStore.containsAlias(alias)) return false
        keyStore.deleteEntry(alias)
        files.persist(ServerTlsStore.KEY, keyStore)
        return true
    }

    /** Exports only a public certificate in PEM encoding. */
    @Synchronized
    override fun exportPublicCertificate(alias: String): String? {
        certificatePolicy.validateAlias(alias)
        val certificate = files.load(ServerTlsStore.KEY).getCertificate(alias) as? X509Certificate ?: return null
        return certificatePolicy.certificateInfo(certificate, includePem = true).pem
    }

    private fun entry(
        store: ServerTlsStore,
        alias: String,
        includePem: Boolean,
    ): ServerTlsEntry? {
        val keyStore = files.load(store)
        if (!keyStore.aliases().toList().contains(alias)) return null
        val chain = keyStore.getCertificateChain(alias)?.toList() ?: listOfNotNull(keyStore.getCertificate(alias))
        val certificates =
            chain.mapNotNull { it as? X509Certificate }.mapIndexed { index, certificate ->
                certificatePolicy.certificateInfo(
                    certificate,
                    includePem && index == 0,
                )
            }
        return ServerTlsEntry(alias, if (keyStore.isKeyEntry(alias)) ENTRY_PRIVATE_KEY else ENTRY_TRUSTED_CERTIFICATE, certificates)
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
        const val ENTRY_PRIVATE_KEY = "private_key"
        const val ENTRY_TRUSTED_CERTIFICATE = "trusted_certificate"
        const val PKCS12_TYPE = "PKCS12"
        const val TLS_PROTOCOL = "TLS"
        const val MAX_SUBJECT_CHARS = 512
        const val MAX_SANS = 20
        const val MIN_VALIDITY_DAYS = 1
        const val MAX_VALIDITY_DAYS = 825
        const val TEMP_PREFIX = ".generated-keystore-"
        const val PKCS12_SUFFIX = ".p12"
    }
}
