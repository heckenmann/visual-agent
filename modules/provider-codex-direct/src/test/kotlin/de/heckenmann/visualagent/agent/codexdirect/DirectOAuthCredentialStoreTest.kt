package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.test.StepVerifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DirectOAuthCredentialStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `credentials survive a new store instance and reject another profile`() {
        val reference = UUID.randomUUID().toString()
        val store = store()
        StepVerifier.create(store.save(reference, record(), null)).expectNext(true).verifyComplete()
        StepVerifier.create(store().load(reference, "profile-1")).expectNext(record()).verifyComplete()
        StepVerifier.create(store.load(reference, "profile-2")).expectError(IllegalArgumentException::class.java).verify()
        assertFalse(record().toString().contains("access-secret"))
    }

    @Test
    fun `signout wins over a refresh based on an old generation`() {
        val reference = UUID.randomUUID().toString()
        val store = store()
        StepVerifier.create(store.save(reference, record(), null)).expectNext(true).verifyComplete()
        StepVerifier.create(store.signOut(reference, "profile-1")).expectNext(true).verifyComplete()
        StepVerifier.create(store.save(reference, record().copy(generation = 1), 0)).expectNext(false).verifyComplete()
        StepVerifier
            .create(store.load(reference, "profile-1"))
            .assertNext {
                assertEquals("", it.accessToken)
                assertEquals("", it.refreshToken)
            }.verifyComplete()
    }

    @Test
    fun `invalid credential references cannot escape the protected directory`() {
        StepVerifier
            .create(store().load("../outside", "profile-1"))
            .expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `new records have owner only permissions on POSIX filesystems`() {
        val reference = UUID.randomUUID().toString()
        StepVerifier.create(store().save(reference, record(), null)).expectNext(true).verifyComplete()
        val root = directory.resolve("oauth")
        if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve("$reference.json")))
        }
    }

    private fun store() = DirectOAuthCredentialStore(DirectCredentialDirectory(directory.resolve("oauth")))

    private fun record() =
        DirectOAuthCredentials(
            "profile-1",
            "issued-client",
            "subject",
            "https://auth.openai.com",
            "access-secret",
            "refresh-secret",
            "identity-secret",
            1000,
            setOf("chatgpt.tokens.use.direct"),
        )
}
