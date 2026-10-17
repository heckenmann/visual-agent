package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises cancellation and error mapping at the coroutine/Reactor boundary. */
class PromptInterpolationPortTest {
    private val service = mockk<PromptInterpolationService>()
    private val port = SpringConversationPort(mockk<AgentManager>(), AppConfigBean(), mockk(relaxed = true), service)

    @Test
    fun `returns the result and releases its application token`() =
        runTest {
            val token = slot<CancellationToken>()
            every { service.interpolate("draft", capture(token)) } returns Mono.just("refined")
            assertEquals("refined", port.interpolate("draft", CancellationTokenImpl()))
            assertTrue(token.captured.isCancelled)
        }

    @Test
    fun `service failures use the safe protocol contract`() =
        runTest {
            every { service.interpolate(any(), any()) } returns Mono.error(IllegalArgumentException("Shorten the draft"))
            assertFailsWith<ProtocolOperationException> { port.interpolate("draft", CancellationTokenImpl()) }
        }

    @Test
    fun `protocol and coroutine cancellation release suspended provider work`() =
        runTest {
            val subscribed = CompletableDeferred<Unit>()
            val token = slot<CancellationToken>()
            var disposed = false
            every { service.interpolate(any(), capture(token)) } returns
                Mono.never<String>().doOnSubscribe { subscribed.complete(Unit) }.doOnCancel { disposed = true }
            val protocolToken = CancellationTokenImpl()
            val pending = async { port.interpolate("draft", protocolToken) }
            subscribed.await()
            protocolToken.cancel()
            assertTrue(token.captured.isCancelled)
            pending.cancel()
            pending.join()
            assertTrue(disposed)
        }
}
