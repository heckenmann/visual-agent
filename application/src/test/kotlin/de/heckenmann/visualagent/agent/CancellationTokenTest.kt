package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CancellationTokenTest {
    @Test
    fun `token starts active`() {
        val token = CancellationToken()
        assertFalse(token.isCancelled)
    }

    @Test
    fun `cancel moves token to cancelled state`() {
        val token = CancellationToken()
        token.cancel()
        assertTrue(token.isCancelled)
    }

    @Test
    fun `cancel is idempotent`() {
        val token = CancellationToken()
        token.cancel()
        token.cancel()
        assertTrue(token.isCancelled)
    }

    @Test
    fun `throwIfCancelled throws after cancel`() {
        val token = CancellationToken()
        token.cancel()
        val result = runCatching { token.throwIfCancelled() }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `throwIfCancelled is a no-op while active`() {
        val token = CancellationToken()
        token.throwIfCancelled()
    }

    @Test
    fun `onCancelled listener is invoked when token is cancelled`() {
        val token = CancellationToken()
        var invoked = false
        token.onCancelled { invoked = true }
        token.cancel()
        assertTrue(invoked)
    }

    @Test
    fun `onCancelled listener is invoked immediately for already cancelled token`() {
        val token = CancellationToken()
        token.cancel()
        var invoked = false
        token.onCancelled { invoked = true }
        assertTrue(invoked)
    }

    @Test
    fun `multiple listeners are all invoked`() {
        val token = CancellationToken()
        var first = false
        var second = false
        token.onCancelled { first = true }
        token.onCancelled { second = true }
        token.cancel()
        assertTrue(first)
        assertTrue(second)
    }
}
