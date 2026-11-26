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
import java.util.concurrent.TimeUnit

/** Persists profile-bound OAuth credentials atomically outside exported provider settings. */
@Service
internal class DirectOAuthCredentialStore(
    private val location: DirectCredentialDirectory,
) {
    private val lock = Any()
    private val drafts = mutableMapOf<String, DirectOAuthCredentials>()

    /** Holds an onboarding registration in memory until the profile is saved. */
    fun stage(
        reference: String,
        record: DirectOAuthCredentials,
    ): Mono<Boolean> =
        io {
            require(drafts.size < 8) { "Too many staged ChatGPT registrations" }
            check(drafts.putIfAbsent(reference, record) == null)
            Schedulers.parallel().schedule({ synchronized(lock) { drafts.remove(reference) } }, 15, TimeUnit.MINUTES)
            true
        }

    /** Moves the selected draft registration to protected storage before the catalog attaches it. */
    fun promote(
        reference: String,
        profileId: String,
    ): Mono<Void> =
        io {
            val record = checkNotNull(drafts[reference]) { "The onboarding sign-in expired. Sign in again." }
            require(record.profileId == profileId) { "Credential record belongs to another provider profile" }
            drafts.remove(reference)
            record.copy(generation = 0)
        }.flatMap { record ->
            save(reference, record, null).doOnNext { saved -> check(saved) { "The credential registration already exists" } }
        }.then()

    /** Loads one protected record only for its owning provider profile. */
    fun load(
        reference: String,
        profileId: String,
    ): Mono<DirectOAuthCredentials> =
        io {
            drafts[reference]?.let { record ->
                require(record.profileId == profileId) { "Credential record belongs to another provider profile" }
                return@io record
            }
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
            drafts[reference]?.let { previous ->
                require(previous.profileId == record.profileId) { "Credential record belongs to another provider profile" }
                if (previous.generation != expectedGeneration) return@io false
                require(record.generation == previous.generation + 1) { "Invalid credential generation" }
                drafts[reference] = record
                return@io true
            }
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
        io {
            drafts[reference]?.let { record ->
                require(record.profileId == profileId) { "Credential record belongs to another provider profile" }
                drafts.remove(reference)
                true
            } ?: false
        }.flatMap { removed ->
            if (removed) Mono.just(true) else invalidateStored(reference, profileId)
        }

    private fun invalidateStored(
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
