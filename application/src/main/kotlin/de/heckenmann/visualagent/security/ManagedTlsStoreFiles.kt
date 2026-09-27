package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64

/** Owns private files and crash-recoverable persistence for managed TLS stores. */
internal class ManagedTlsStoreFiles(
    serverDataRoot: Path,
) {
    val directory: Path =
        serverDataRoot
            .resolve(SECURITY_DIRECTORY)
            .resolve(TLS_DIRECTORY)
            .toAbsolutePath()
            .normalize()
    private val trustStoreFile = directory.resolve(TRUST_STORE_FILE)
    private val keyStoreFile = directory.resolve(KEY_STORE_FILE)
    private val trustPasswordFile = directory.resolve(TRUST_PASSWORD_FILE)
    private val keyPasswordFile = directory.resolve(KEY_PASSWORD_FILE)

    /** Loads the requested PKCS#12 store, initializing an empty managed store when missing. */
    @Synchronized
    fun load(store: ServerTlsStore): KeyStore {
        prepareDirectory()
        val password = password(store)
        val keyStore = KeyStore.getInstance(STORE_TYPE)
        val path = storePath(store)
        if (Files.exists(path)) {
            try {
                Files.newInputStream(path).use { keyStore.load(it, password) }
            } finally {
                password.fill('\u0000')
            }
        } else {
            try {
                keyStore.load(null, password)
            } finally {
                password.fill('\u0000')
            }
            persist(store, keyStore)
        }
        return keyStore
    }

    /** Persists one complete store atomically and retains the previous valid version as a backup. */
    @Synchronized
    fun persist(
        store: ServerTlsStore,
        keyStore: KeyStore,
    ) {
        prepareDirectory()
        val path = storePath(store)
        val temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            val password = password(store)
            try {
                Files.newOutputStream(temporary).use { output -> keyStore.store(output, password) }
            } finally {
                password.fill('\u0000')
            }
            restrictFile(temporary)
            if (Files.exists(path)) {
                Files.copy(path, backupPath(store), REPLACE_EXISTING)
                restrictFile(backupPath(store))
            }
            moveIntoPlace(temporary, path)
            restrictFile(path)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Returns the key-store password file for the fixed keytool invocation. */
    fun passwordFile(store: ServerTlsStore): Path {
        prepareDirectory()
        password(store).fill('\u0000')
        return if (store == ServerTlsStore.KEY) keyPasswordFile else trustPasswordFile
    }

    /** Returns the requested store password as a mutable character array. */
    fun passwordChars(store: ServerTlsStore): CharArray = password(store)

    /** Returns the canonical path for one of the two managed stores. */
    fun storePath(store: ServerTlsStore): Path = if (store == ServerTlsStore.KEY) keyStoreFile else trustStoreFile

    private fun password(store: ServerTlsStore): CharArray {
        val path = if (store == ServerTlsStore.KEY) keyPasswordFile else trustPasswordFile
        if (!Files.exists(path)) {
            val bytes = ByteArray(PASSWORD_RANDOM_BYTES).also(SecureRandom()::nextBytes)
            val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            bytes.fill(0)
            try {
                try {
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE))
                } catch (_: UnsupportedOperationException) {
                    Files.createFile(path)
                }
                Files.writeString(path, generated)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                // Another server component initialized the same managed store concurrently.
            }
        }
        restrictFile(path)
        return Files.readString(path).trim().toCharArray()
    }

    private fun prepareDirectory() {
        Files.createDirectories(directory)
        runCatching { Files.setPosixFilePermissions(directory, OWNER_READ_WRITE_EXECUTE) }
    }

    private fun backupPath(store: ServerTlsStore): Path =
        directory.resolve(
            if (store ==
                ServerTlsStore.KEY
            ) {
                KEY_BACKUP_FILE
            } else {
                TRUST_BACKUP_FILE
            },
        )

    private fun moveIntoPlace(
        source: Path,
        destination: Path,
    ) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }

    private fun restrictFile(path: Path) {
        runCatching { Files.setPosixFilePermissions(path, OWNER_READ_WRITE) }
    }

    private companion object {
        const val SECURITY_DIRECTORY = "security"
        const val TLS_DIRECTORY = "tls"
        const val STORE_TYPE = "PKCS12"
        const val TRUST_STORE_FILE = "truststore.p12"
        const val KEY_STORE_FILE = "keystore.p12"
        const val TRUST_PASSWORD_FILE = ".truststore-password"
        const val KEY_PASSWORD_FILE = ".keystore-password"
        const val TRUST_BACKUP_FILE = "truststore.p12.backup"
        const val KEY_BACKUP_FILE = "keystore.p12.backup"
        const val TEMP_PREFIX = ".tls-store-"
        const val TEMP_SUFFIX = ".tmp"
        const val PASSWORD_RANDOM_BYTES = 32
        val OWNER_READ_WRITE: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
        val OWNER_READ_WRITE_EXECUTE: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
    }
}
