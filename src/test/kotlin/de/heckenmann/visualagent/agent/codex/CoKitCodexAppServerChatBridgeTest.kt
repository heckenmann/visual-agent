package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.client.ApprovalPolicy
import io.github.vupoint.cokit.client.CodexClient
import io.github.vupoint.cokit.client.CodexJsonPayload
import io.github.vupoint.cokit.client.CodexNotification
import io.github.vupoint.cokit.client.CodexRpcMethod
import io.github.vupoint.cokit.client.CodexRpcUnit
import io.github.vupoint.cokit.client.DynamicToolCallHandler
import io.github.vupoint.cokit.client.DynamicToolCallRequest
import io.github.vupoint.cokit.client.ItemId
import io.github.vupoint.cokit.client.SandboxMode
import io.github.vupoint.cokit.client.SandboxPolicy
import io.github.vupoint.cokit.client.Thread
import io.github.vupoint.cokit.client.ThreadId
import io.github.vupoint.cokit.client.ThreadInjectItemsParams
import io.github.vupoint.cokit.client.ThreadStartParams
import io.github.vupoint.cokit.client.ThreadStartResult
import io.github.vupoint.cokit.client.Turn
import io.github.vupoint.cokit.client.TurnId
import io.github.vupoint.cokit.client.TurnStartParams
import io.github.vupoint.cokit.client.TurnStartResult
import io.github.vupoint.cokit.client.TurnStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            assertEquals(SandboxMode.WorkspaceWrite, (params.getValue("thread/start") as ThreadStartParams).sandbox)
            assertEquals(ApprovalPolicy.OnRequest, (params.getValue("turn/start") as TurnStartParams).approvalPolicy)
            assertEquals(SandboxPolicy.WorkspaceWrite, (params.getValue("turn/start") as TurnStartParams).sandbox)
            val encodedStart = Json.encodeToString(params.getValue("thread/start") as ThreadStartParams)
            assertTrue(encodedStart.contains("\"approvalPolicy\":\"on-request\""))
            assertTrue(encodedStart.contains("\"sandbox\":\"workspace-write\""))
            val encodedTurn = Json.encodeToString(params.getValue("turn/start") as TurnStartParams)
            assertTrue(encodedTurn.contains("\"sandboxPolicy\":{\"type\":\"workspaceWrite\"}"))
        }

    @Test
    fun `preserves roles and executes only declared dynamic tools`() =
        runBlocking {
            val notifications = MutableSharedFlow<CodexNotification>(extraBufferCapacity = 8)
            val methods = mutableListOf<String>()
            val params = mutableMapOf<String, Any>()
            val handlers = mutableListOf<DynamicToolCallHandler>()
            val client = fakeClient(notifications, methods, params, handlers)
            val connector = CodexAppServerConnector { _, _ -> CodexAppServerConnection(client, AutoCloseable {}) }
            val bridge = CoKitCodexAppServerChatBridge(connector, Path.of("/codex"), Path.of("/workspace"), "model")
            val prompt =
                Prompt(
                    listOf(
                        SystemMessage("Follow application policy"),
                        UserMessage("Earlier question"),
                        AssistantMessage("Earlier answer"),
                        UserMessage("Current question"),
                    ),
                )

            bridge.complete(prompt, toolCallbacks = listOf(recordingToolCallback()))

            assertEquals(listOf("thread/start", "thread/injectItems", "turn/start", "thread/delete"), methods)
            val threadStart = params.getValue("thread/start") as ThreadStartParams
            assertEquals("Follow application policy", threadStart.developerInstructions)
            assertEquals(listOf("todos"), threadStart.dynamicTools?.map { it.name })
            val injected = params.getValue("thread/injectItems") as ThreadInjectItemsParams
            assertEquals(2, injected.items.size)
            assertEquals(true, injected.items[0].toJsonString().contains("\"role\":\"user\""))
            assertEquals(true, injected.items[1].toJsonString().contains("\"role\":\"assistant\""))
            val turnStart = params.getValue("turn/start") as TurnStartParams
            assertEquals("Current question", (turnStart.input.single() as io.github.vupoint.cokit.client.TurnInput.Text).text)
            val toolResult =
                handlers.single().call(
                    DynamicToolCallRequest(
                        threadId = ThreadId("thread"),
                        turnId = TurnId("turn"),
                        callId = "call",
                        tool = "todos",
                        arguments = CodexJsonPayload.parse("{\"action\":\"list\"}"),
                    ),
                )
            assertEquals(true, toolResult.success)
            assertEquals("handled {\"action\":\"list\"}", toolResult.contentItems.single().text)
        }

    private fun fakeClient(
        notifications: MutableSharedFlow<CodexNotification>,
        methods: MutableList<String>,
        params: MutableMap<String, Any>,
        handlers: MutableList<DynamicToolCallHandler> = mutableListOf(),
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
                        "thread/injectItems" -> CodexRpcUnit
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
                "registerDynamicToolCallHandler" -> {
                    handlers += arguments?.get(0) as DynamicToolCallHandler
                    Unit
                }
                "close" -> Unit
                "isInitialized" -> true
                else -> null
            }
        } as CodexClient
    }

    private fun recordingToolCallback(): ToolCallback =
        object : ToolCallback {
            override fun getToolDefinition(): ToolDefinition =
                ToolDefinition
                    .builder()
                    .name("todos")
                    .description("Manage todos")
                    .inputSchema("{\"type\":\"object\"}")
                    .build()

            override fun call(functionInput: String): String = "handled $functionInput"
        }
}
