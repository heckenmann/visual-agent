package de.heckenmann.visualagent.security

import java.security.KeyStore

/** Restricts a server key manager to the single alias selected for the TLS listener. */
internal class ManagedTlsKeyStoreSelector {
    /** Copies only the selected private key and certificate chain into a temporary in-memory store. */
    fun select(
        source: KeyStore,
        alias: String,
        password: CharArray,
    ): KeyStore =
        KeyStore
            .getInstance(PKCS12_TYPE)
            .apply {
                load(null, password)
                setKeyEntry(alias, source.getKey(alias, password), password, source.getCertificateChain(alias))
            }

    private companion object {
        const val PKCS12_TYPE = "PKCS12"
    }
}
