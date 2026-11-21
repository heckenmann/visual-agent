package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies callback correlation for Codex app-server dynamic tool requests. */
class CodexAppServerDynamicToolTest {
    @Test
    fun `native deltas are exposed as spring responses and dynamic tools receive results`() =
        runBlocking {
            val directory = createTempDirectory("codex-app-server-test-")
            val fixture = CodexAppServerChatModelTest()
            val executable = fixture.fakeServer(directory)
            val receivedArguments = AtomicReference<String>()
            val recordedCall = AtomicReference<String>()
            val receivedParent = AtomicReference<String>()
            val callback =
                object : ToolCallback {
                    override fun getToolDefinition(): ToolDefinition =
                        ToolDefinition
                            .builder()
                            .name("context")
                            .description("Returns context")
                            .inputSchema("{\"type\":\"object\"}")
                            .build()

                    override fun call(functionInput: String): String = error("Tool context is required")

                    override fun call(
                        functionInput: String,
                        toolContext: ToolContext?,
                    ): String {
                        receivedParent.set(toolContext?.context?.get("parentAssistantTurnId") as? String)
                        receivedArguments.set(functionInput)
                        return "tool-result:$functionInput"
                    }
                }
            try {
                val model =
                    CodexAppServerChatModel(executable, "gpt-test", listOf(callback), directory) { turn ->
                        recordedCall.set(turn.toolCalls.single().id)
                        "assistant-turn"
                    }
                val responses =
                    model
                        .streamReactive(Prompt("hello"))
                        .collectList()
                        .block()
                        .orEmpty()

                assertEquals(
                    listOf("hel", "lo", ""),
                    responses.map {
                        it.result
                            ?.output
                            ?.text
                            .orEmpty()
                    },
                )
                assertTrue(responses.last().hasFinishReasons(setOf("stop")))
                assertEquals("{}", receivedArguments.get())
                assertEquals("call-1", recordedCall.get())
                assertEquals("assistant-turn", receivedParent.get())
            } finally {
                fixture.deleteRecursively(directory)
            }
        }
}
