package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.tools.GeneratedServerCertificate
import de.heckenmann.visualagent.agent.tools.ServerTlsEntry
import de.heckenmann.visualagent.agent.tools.ServerTlsMaterialPort
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.SslBundleKey
import org.springframework.boot.ssl.SslManagerBundle
import org.springframework.boot.ssl.SslOptions
import org.springframework.boot.ssl.SslStoreBundle
import org.springframework.stereotype.Component
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.function.Supplier
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Owns the managed TLS stores below the server data root and exposes secret-safe operations. */
@Component
class ManagedTlsMaterialService internal constructor(
    private val files: ManagedTlsStoreFiles,
    private val mutationAudit: TlsMutationAudit,
    private val certificateOperations: ManagedTlsCertificateOperations,
) : ServerTlsMaterialPort,
    ServerTrustManagerProvider,
    Supplier<X509TrustManager> {
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
    ): ServerTlsEntry =
        mutationAudit.record("import_certificate", ServerTlsStore.TRUST, alias, { certificateFingerprint(ServerTlsStore.TRUST, alias) }) {
            certificatePolicy.validateAlias(alias)
            val trusted = certificatePolicy.parseCaCertificate(certificate)
            val keyStore = files.load(ServerTlsStore.TRUST)
            require(!keyStore.containsAlias(alias)) { "A trust-store entry already exists for that alias." }
            keyStore.setCertificateEntry(alias, trusted)
            files.persist(ServerTlsStore.TRUST, keyStore)
            checkNotNull(entry(ServerTlsStore.TRUST, alias, includePem = false))
        }

    /** Removes one exact public certificate alias from the managed trust store. */
    @Synchronized
    override fun removeTrustedCertificate(alias: String): Boolean {
        var fingerprint: String? = null
        return mutationAudit.record(
            "remove_certificate",
            ServerTlsStore.TRUST,
            alias,
            { fingerprint },
            action = {
                certificatePolicy.validateAlias(alias)
                val keyStore = files.load(ServerTlsStore.TRUST)
                if (!keyStore.isCertificateEntry(alias)) {
                    false
                } else {
                    fingerprint = certificateFingerprint(ServerTlsStore.TRUST, alias)
                    keyStore.deleteEntry(alias)
                    files.persist(ServerTlsStore.TRUST, keyStore)
                    true
                }
            },
            outcome = { if (it) "success" else "not_found" },
        )
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
    ): GeneratedServerCertificate =
        certificateOperations.generate(alias, subject, dnsNames, ipAddresses, certificateAuthority, validityDays, signingCaAlias)

    /** Removes one exact alias from the managed private key store. */
    @Synchronized
    override fun removeKeyEntry(alias: String): Boolean {
        var fingerprint: String? = null
        return mutationAudit.record(
            "remove_key_entry",
            ServerTlsStore.KEY,
            alias,
            { fingerprint },
            action = {
                certificatePolicy.validateAlias(alias)
                val keyStore = files.load(ServerTlsStore.KEY)
                if (!keyStore.containsAlias(alias)) {
                    false
                } else {
                    fingerprint = certificateFingerprint(ServerTlsStore.KEY, alias)
                    keyStore.deleteEntry(alias)
                    files.persist(ServerTlsStore.KEY, keyStore)
                    true
                }
            },
            outcome = { if (it) "success" else "not_found" },
        )
    }

    private fun certificateFingerprint(
        store: ServerTlsStore,
        alias: String,
    ): String? {
        certificatePolicy.validateAlias(alias)
        val certificate = files.load(store).getCertificate(alias) as? X509Certificate ?: return null
        return certificatePolicy.certificateInfo(certificate, includePem = false).sha256
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

    private companion object {
        const val ENTRY_PRIVATE_KEY = "private_key"
        const val ENTRY_TRUSTED_CERTIFICATE = "trusted_certificate"
        const val PKCS12_TYPE = "PKCS12"
        const val TLS_PROTOCOL = "TLS"
    }
}
