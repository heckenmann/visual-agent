package de.heckenmann.visualagent.agent.codexdirect

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** Persists profile-bound OAuth credentials atomically outside exported provider settings. */
@Service
internal class DirectOAuthCredentialStore(
    private val location: DirectCredentialDirectory,
) {
    private val lock = Any()

    /** Loads one protected record only for its owning provider profile. */
    fun load(
        reference: String,
        profileId: String,
    ): Mono<DirectOAuthCredentials> =
        io {
            val path = root().resolve(fileName(reference))
            if (!Files.exists(path, NOFOLLOW_LINKS)) return@io null
            requireProtectedPath(path)
            require(Files.size(path) <= MAX_BYTES) { "Credential record exceeds storage limit" }
            val record =
                Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
                    val bytes = input.readNBytes(MAX_BYTES + 1)
                    require(bytes.size <= MAX_BYTES) { "Credential record exceeds storage limit" }
                    Json.decodeFromString<DirectOAuthCredentials>(String(bytes, StandardCharsets.UTF_8))
                }
            require(record.profileId == profileId) { "Credential record belongs to another provider profile" }
            record
        }

    /** Replaces a record only when its generation matches; sign-out cannot be undone by stale refresh. */
    fun save(
        reference: String,
        record: DirectOAuthCredentials,
        expectedGeneration: Long?,
    ): Mono<Boolean> =
        io {
            val directory = root()
            val lockPath = directory.resolve("credentials.lock")
            if (!Files.exists(lockPath, NOFOLLOW_LINKS)) {
                runCatching { Files.createFile(lockPath, *protectedAttributes(directory, false)) }
                    .getOrElse { error -> if (!Files.exists(lockPath, NOFOLLOW_LINKS)) throw error }
            }
            requireProtectedPath(lockPath)
            FileChannel.open(lockPath, WRITE, NOFOLLOW_LINKS).use { channel ->
                channel.lock().use {
                    val path = directory.resolve(fileName(reference))
                    val previous =
                        if (Files.exists(path, NOFOLLOW_LINKS)) {
                            requireProtectedPath(path)
                            require(Files.size(path) <= MAX_BYTES) { "Credential record exceeds storage limit" }
                            Json.decodeFromString<DirectOAuthCredentials>(Files.readString(path))
                        } else {
                            null
                        }
                    require(
                        previous == null || previous.profileId == record.profileId,
                    ) { "Credential record belongs to another provider profile" }
                    if (previous?.generation != expectedGeneration) return@io false
                    require(record.generation == (expectedGeneration?.plus(1) ?: 0)) { "Invalid credential generation" }
                    val bytes = Json.encodeToString(record).toByteArray(StandardCharsets.UTF_8)
                    require(bytes.size <= MAX_BYTES) { "Credential record exceeds storage limit" }
                    val temporary = Files.createTempFile(directory, ".oauth-", ".tmp", *protectedAttributes(directory, false))
                    try {
                        Files.write(temporary, bytes)
                        Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
                    } finally {
                        Files.deleteIfExists(temporary)
                    }
                    true
                }
            }
        }

    /** Invalidates local tokens so a refresh based on an older generation cannot restore them. */
    fun signOut(
        reference: String,
        profileId: String,
    ): Mono<Boolean> =
        load(reference, profileId)
            .flatMap { record ->
                save(
                    reference,
                    record.copy(
                        accessToken = "",
                        refreshToken = "",
                        idToken = "",
                        scopes = emptySet(),
                        expiresAtEpochSeconds = 0,
                        generation = record.generation + 1,
                    ),
                    record.generation,
                )
            }.defaultIfEmpty(true)

    private fun root(): java.nio.file.Path {
        val directory = location.path.toAbsolutePath().normalize()
        val parent = requireNotNull(directory.parent)
        Files.createDirectories(parent)
        if (!Files.exists(directory, NOFOLLOW_LINKS)) Files.createDirectory(directory, *protectedAttributes(parent, true))
        requireProtectedPath(directory)
        return directory
    }

    private fun fileName(reference: String): String {
        require(UUID.fromString(reference).toString() == reference) { "Invalid credential reference" }
        return "$reference.json"
    }

    private fun <T : Any> io(action: () -> T?): Mono<T> =
        Mono
            .fromCallable { synchronized(lock) { action() } }
            .subscribeOn(Schedulers.boundedElastic())

    private companion object {
        const val MAX_BYTES = 65_536
    }
}
