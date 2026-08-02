package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import io.github.vupoint.cokit.client.CodexClient
import io.github.vupoint.cokit.client.CodexNotification
import io.github.vupoint.cokit.client.CodexRpcMethod
import io.github.vupoint.cokit.client.CodexRpcUnit
import io.github.vupoint.cokit.client.ItemId
import io.github.vupoint.cokit.client.Thread
import io.github.vupoint.cokit.client.ThreadId
import io.github.vupoint.cokit.client.ThreadStartResult
import io.github.vupoint.cokit.client.Turn
import io.github.vupoint.cokit.client.TurnId
import io.github.vupoint.cokit.client.TurnStartResult
import io.github.vupoint.cokit.client.TurnStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodexCliProviderTest {
    @Test
    fun `chat and stream map Codex app-server responses`() =
        runBlocking {
            val notifications = MutableSharedFlow<CodexNotification>(extraBufferCapacity = 8)
            val provider = provider(notifications)
            val request = request(model = null)

            val response = provider.chat(request)
            val streamed = provider.stream(request).toList()

            assertEquals("default-model", response.model)
            assertEquals("answer", response.message.content)
            assertEquals(true, response.done)
            assertEquals(listOf("answer", ""), streamed.map { it.message.content })
            assertEquals(listOf(false, true), streamed.map { it.done })
        }

    @Test
    fun `unsupported profileless operations fail explicitly`() =
        runBlocking {
            val provider = provider(MutableSharedFlow(extraBufferCapacity = 8))

            assertFailsWith<IllegalStateException> { provider.chat(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.stream(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.vision(byteArrayOf(), "describe") }
            assertFailsWith<IllegalStateException> { provider.getModels() }
            assertEquals(emptyList(), provider.embeddings("text"))
            assertEquals(true, provider.isConnected())
            assertEquals(false, provider.checkConnection())
            assertEquals("model", provider.getModelDetails("model").model)
        }

    private fun provider(notifications: MutableSharedFlow<CodexNotification>): CodexCliProvider {
        val locator = mockk<CodexCliLocator>()
        coEvery { locator.locate(any()) } returns
            CodexCliLocation.Ready(Path.of("/codex"), "codex-cli test", CodexCliLocationSource.EXPLICIT)
        val toolRegistry = mockk<ToolRegistry>()
        every { toolRegistry.functionCallbacks(any(), any()) } returns emptyList()
        val connector = CodexAppServerConnector { _, _ -> CodexAppServerConnection(fakeClient(notifications), AutoCloseable {}) }
        return CodexCliProvider(locator, connector, toolRegistry)
    }

    private fun request(model: String?): ChatRequestContext =
        ChatRequestContext(
            messages = listOf(Message("system", "policy"), Message("assistant", "earlier"), Message("user", "hello")),
            model = model,
            providerProfile = ProviderProfile("codex", "Codex", ProviderAdapter.CODEX_CLI, "", defaultModel = "default-model"),
        )

    private fun fakeClient(notifications: MutableSharedFlow<CodexNotification>): CodexClient {
        val threadId = ThreadId("thread")
        val turnId = TurnId("turn")
        return Proxy.newProxyInstance(CodexClient::class.java.classLoader, arrayOf(CodexClient::class.java)) { _, method, arguments ->
            when (method.name) {
                "getNotifications" -> notifications
                "request" -> {
                    val rpc = arguments?.get(0) as CodexRpcMethod<*, *>
                    when (rpc.method) {
                        "thread/start" -> ThreadStartResult(Thread(threadId))
                        "thread/inject_items", "thread/delete" -> CodexRpcUnit
                        "turn/start" -> {
                            notifications.tryEmit(CodexNotification.AgentMessageDelta(threadId, turnId, ItemId("item"), "answer"))
                            notifications.tryEmit(CodexNotification.TurnCompleted(Turn(turnId, TurnStatus.Completed)))
                            TurnStartResult(Turn(turnId, TurnStatus.InProgress))
                        }
                        else -> error("Unexpected RPC ${rpc.method}")
                    }
                }
                "registerDynamicToolCallHandler", "close" -> Unit
                "isInitialized" -> true
                else -> null
            }
        } as CodexClient
    }
}
