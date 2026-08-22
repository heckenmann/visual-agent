package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.Message
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies provider behavior that does not require a real Codex subscription. */
class CodexCliProviderTest {
    @Test
    fun `profileless operations fail explicitly`() =
        runBlocking {
            val provider = CodexCliProvider(mockk(), mockk())

            assertFailsWith<IllegalStateException> { provider.chat(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.stream(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.vision(byteArrayOf(), "describe") }
            assertFailsWith<IllegalStateException> { provider.getModels() }
            assertEquals(emptyList(), provider.embeddings("text"))
            assertEquals(true, provider.isConnected())
            assertEquals(false, provider.checkConnection())
            assertEquals("model", provider.getModelDetails("model").model)
        }
}
