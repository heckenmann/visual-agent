package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.client.ApprovalPolicy
import io.github.vupoint.cokit.client.CodexClient
import io.github.vupoint.cokit.client.CodexNotification
import io.github.vupoint.cokit.client.CodexRpcMethod
import io.github.vupoint.cokit.client.CodexRpcUnit
import io.github.vupoint.cokit.client.ItemId
import io.github.vupoint.cokit.client.SandboxPolicy
import io.github.vupoint.cokit.client.Thread
import io.github.vupoint.cokit.client.ThreadId
import io.github.vupoint.cokit.client.ThreadStartParams
import io.github.vupoint.cokit.client.ThreadStartResult
import io.github.vupoint.cokit.client.Turn
import io.github.vupoint.cokit.client.TurnId
import io.github.vupoint.cokit.client.TurnStartParams
import io.github.vupoint.cokit.client.TurnStartResult
import io.github.vupoint.cokit.client.TurnStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.prompt.Prompt
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.test.assertEquals

internal class CoKitCodexAppServerChatBridgeTest {
    @Test
    fun `streams deltas and cleans up completed turn`() =
        runBlocking {
            val notifications = MutableSharedFlow<CodexNotification>(extraBufferCapacity = 8)
            val methods = mutableListOf<String>()
            val params = mutableMapOf<String, Any>()
            val client = fakeClient(notifications, methods, params)
            val connector =
                CodexAppServerConnector { _, _ ->
                    CodexAppServerConnection(client, AutoCloseable {})
                }
            val bridge = CoKitCodexAppServerChatBridge(connector, Path.of("/codex"), Path.of("/workspace"), "model")

            val result = bridge.complete(Prompt("hello"))

            assertEquals("first second", result.content)
            assertEquals(listOf("thread/start", "turn/start", "thread/delete"), methods)
            assertEquals(ApprovalPolicy.OnRequest, (params.getValue("thread/start") as ThreadStartParams).approvalPolicy)
            assertEquals(SandboxPolicy.WorkspaceWrite, (params.getValue("thread/start") as ThreadStartParams).sandbox)
            assertEquals(ApprovalPolicy.OnRequest, (params.getValue("turn/start") as TurnStartParams).approvalPolicy)
            assertEquals(SandboxPolicy.WorkspaceWrite, (params.getValue("turn/start") as TurnStartParams).sandbox)
        }

    private fun fakeClient(
        notifications: MutableSharedFlow<CodexNotification>,
        methods: MutableList<String>,
        params: MutableMap<String, Any>,
    ): CodexClient {
        val threadId = ThreadId("thread")
        val turnId = TurnId("turn")
        return Proxy.newProxyInstance(
            CodexClient::class.java.classLoader,
            arrayOf(CodexClient::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getNotifications" -> notifications
                "request" -> {
                    val rpc = arguments?.get(0) as CodexRpcMethod<*, *>
                    methods += rpc.method
                    params[rpc.method] = requireNotNull(arguments[1])
                    when (rpc.method) {
                        "thread/start" -> ThreadStartResult(Thread(threadId))
                        "turn/start" -> {
                            notifications.tryEmit(CodexNotification.AgentMessageDelta(threadId, turnId, ItemId("item"), "first "))
                            notifications.tryEmit(CodexNotification.AgentMessageDelta(threadId, turnId, ItemId("item"), "second"))
                            notifications.tryEmit(CodexNotification.TurnCompleted(Turn(turnId, TurnStatus.Completed)))
                            TurnStartResult(Turn(turnId, TurnStatus.InProgress))
                        }
                        "thread/delete" -> CodexRpcUnit
                        else -> error("Unexpected RPC ${rpc.method}")
                    }
                }
                "close" -> Unit
                "isInitialized" -> true
                else -> null
            }
        } as CodexClient
    }
}
