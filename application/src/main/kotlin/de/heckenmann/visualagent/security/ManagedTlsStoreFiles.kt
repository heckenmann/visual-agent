package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64

/** Owns private files and crash-recoverable persistence for managed TLS stores. */
@Component
internal class ManagedTlsStoreFiles(
    @Qualifier("serverDataRoot") serverDataRoot: Path,
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
        try {
            validateManagedFile(path)
            if (!Files.exists(path, NOFOLLOW_LINKS)) {
                val backup = backupPath(store)
                validateManagedFile(backup)
                if (Files.exists(backup, NOFOLLOW_LINKS)) {
                    val recovered = loadStore(backup, password)
                    restoreBackup(
                        backup,
                        path,
                        IOException("The primary managed TLS store was missing."),
                    )
                    return recovered
                }
                keyStore.load(null, password)
                persist(store, keyStore)
                return keyStore
            }
            try {
                return loadStore(path, password)
            } catch (primaryFailure: Exception) {
                val backup = backupPath(store)
                validateManagedFile(backup)
                if (!Files.exists(backup, NOFOLLOW_LINKS)) throw primaryFailure
                val recovered =
                    try {
                        loadStore(backup, password)
                    } catch (backupFailure: Exception) {
                        primaryFailure.addSuppressed(backupFailure)
                        throw primaryFailure
                    }
                restoreBackup(backup, path, primaryFailure)
                return recovered
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /** Persists one complete store with a matching recovery copy before replacing the primary file. */
    @Synchronized
    fun persist(
        store: ServerTlsStore,
        keyStore: KeyStore,
    ) {
        prepareDirectory()
        val path = storePath(store)
        validateManagedFile(path)
        val backup = backupPath(store)
        validateManagedFile(backup)
        val temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            val password = password(store)
            try {
                Files.newOutputStream(temporary).use { output -> keyStore.store(output, password) }
            } finally {
                password.fill('\u0000')
            }
            restrictFile(temporary)
            replaceBackup(temporary, backup)
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

    @Synchronized
    private fun password(store: ServerTlsStore): CharArray {
        prepareDirectory()
        val path = if (store == ServerTlsStore.KEY) keyPasswordFile else trustPasswordFile
        validateManagedFile(path)
        if (!Files.exists(path, NOFOLLOW_LINKS)) {
            val primaryStore = storePath(store)
            val backupStore = backupPath(store)
            validateManagedFile(primaryStore)
            validateManagedFile(backupStore)
            if (Files.exists(primaryStore, NOFOLLOW_LINKS) || Files.exists(backupStore, NOFOLLOW_LINKS)) {
                throw IOException("Managed TLS store password is missing.")
            }
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
        validateManagedFile(path)
        restrictFile(path)
        val storedPassword = Files.readString(path).trim()
        if (storedPassword.isEmpty()) throw IOException("Managed TLS store password is unavailable.")
        return storedPassword.toCharArray()
    }

    private fun prepareDirectory() {
        val securityDirectory = directory.parent
        if (Files.isSymbolicLink(securityDirectory)) {
            throw java.io.IOException("Managed TLS directories must not be symbolic links.")
        }
        Files.createDirectories(securityDirectory)
        if (Files.isSymbolicLink(securityDirectory) || Files.isSymbolicLink(directory)) {
            throw java.io.IOException("Managed TLS directories must not be symbolic links.")
        }
        Files.createDirectories(directory)
        if (Files.isSymbolicLink(securityDirectory) || Files.isSymbolicLink(directory)) {
            throw java.io.IOException("Managed TLS directories must not be symbolic links.")
        }
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

    private fun replaceBackup(
        source: Path,
        backup: Path,
    ) {
        val temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            Files.copy(source, temporary, REPLACE_EXISTING)
            restrictFile(temporary)
            moveIntoPlace(temporary, backup)
            restrictFile(backup)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun loadStore(
        path: Path,
        password: CharArray,
    ): KeyStore {
        val keyStore = KeyStore.getInstance(STORE_TYPE)
        val passwordCopy = password.clone()
        try {
            Files.newInputStream(path).use { keyStore.load(it, passwordCopy) }
        } finally {
            passwordCopy.fill('\u0000')
        }
        return keyStore
    }

    private fun validateManagedFile(path: Path) {
        if (Files.isSymbolicLink(path)) {
            throw java.io.IOException("Managed TLS files must not be symbolic links.")
        }
        if (Files.exists(path, NOFOLLOW_LINKS) && !Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            throw java.io.IOException("Managed TLS paths must be regular files.")
        }
    }

    private fun restoreBackup(
        backup: Path,
        destination: Path,
        primaryFailure: Exception,
    ) {
        validateManagedFile(backup)
        validateManagedFile(destination)
        val temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            Files.copy(backup, temporary, REPLACE_EXISTING)
            restrictFile(temporary)
            try {
                moveIntoPlace(temporary, destination)
            } catch (restoreFailure: Exception) {
                restoreFailure.addSuppressed(primaryFailure)
                throw restoreFailure
            }
            restrictFile(destination)
        } finally {
            Files.deleteIfExists(temporary)
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
