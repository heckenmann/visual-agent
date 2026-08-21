package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the clean-room Codex app-server protocol adapter. */
class CodexAppServerChatModelTest {
    @Test
    fun `native deltas are exposed as spring responses and dynamic tools receive results`() =
        runBlocking {
            val directory = createTempDirectory("codex-app-server-test-")
            val executable = fakeServer(directory)
            val receivedArguments = AtomicReference<String>()
            val callback =
                object : ToolCallback {
                    override fun getToolDefinition(): ToolDefinition =
                        ToolDefinition
                            .builder()
                            .name("context")
                            .description("Returns context")
                            .inputSchema("{\"type\":\"object\"}")
                            .build()

                    override fun call(functionInput: String): String {
                        receivedArguments.set(functionInput)
                        return "tool-result:$functionInput"
                    }
                }
            try {
                val model = CodexAppServerChatModel(executable, "gpt-test", listOf(callback), directory)
                val responses = model.streamFlow(Prompt("hello")).toList()

                assertEquals(
                    listOf("hel", "lo", ""),
                    responses.map(::responseText),
                )
                assertTrue(responses.last().hasFinishReasons(setOf("stop")))
                assertEquals("{}", receivedArguments.get())
            } finally {
                deleteRecursively(directory)
            }
        }

    @Test
    fun `a single native delta is animated in small chunks`() =
        runBlocking {
            val directory = createTempDirectory("codex-app-server-single-delta-test-")
            val executable = fakeServer(directory, singleDelta = true)
            try {
                val responses =
                    CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory)
                        .streamFlow(Prompt("hello"))
                        .toList()

                assertEquals(
                    listOf("hel", "lo", ""),
                    responses.map(::responseText),
                )
                assertTrue(responses.last().hasFinishReasons(setOf("stop")))
            } finally {
                deleteRecursively(directory)
            }
        }

    @Test
    fun `complete retains all deltas instead of returning the empty terminal chunk`() =
        runBlocking {
            val directory = createTempDirectory("codex-app-server-complete-test-")
            val executable = fakeServer(directory)
            try {
                val response =
                    CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory)
                        .complete(Prompt("hello"))

                assertEquals("hello", responseText(response))
                assertTrue(response.hasFinishReasons(setOf("stop")))
            } finally {
                deleteRecursively(directory)
            }
        }

    @Test
    fun `reasoning summaries are emitted as thinking markup`() =
        runBlocking {
            val directory = createTempDirectory("codex-app-server-reasoning-test-")
            val executable = fakeServer(directory, reasoningSummary = true)
            try {
                val responses =
                    CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory, showReasoningSummary = true)
                        .streamFlow(Prompt("hello"))
                        .toList()

                assertTrue(responses.any { responseText(it) == "<think>planning</think>" })
            } finally {
                deleteRecursively(directory)
            }
        }

    private fun fakeServer(
        directory: Path,
        singleDelta: Boolean = false,
        reasoningSummary: Boolean = false,
    ): Path {
        val executable = directory.resolve("codex")
        val deltaEvents =
            if (singleDelta) {
                """printf '%s\n' '{"jsonrpc":"2.0","method":"item/agentMessage/delta","params":{"delta":"hello","itemId":"item-1","threadId":"thread-1","turnId":"turn-1"}}'"""
            } else {
                deltaEvent("hel") + "\n                  " + deltaEvent("lo")
            }
        Files.writeString(
            executable,
            """
            #!/bin/sh
            while IFS= read -r line; do
              case "${'$'}line" in
                *'"method":"initialize"'*)
                  case "${'$'}line" in
                    *'"experimentalApi":true'*)
                      printf '%s\n' '{"jsonrpc":"2.0","id":1,"result":{"codexHome":"/tmp","platformFamily":"unix","platformOs":"linux","userAgent":"test"}}'
                      ;;
                    *)
                      printf '%s\n' '{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"experimentalApi capability is required"}}'
                      ;;
                  esac
                  ;;
                *'"method":"thread/start"'*)
                  printf '%s\n' '{"jsonrpc":"2.0","id":2,"result":{"thread":{"id":"thread-1"}}}'
                  ;;
                *'"method":"turn/start"'*)
                  printf '%s\n' '{"jsonrpc":"2.0","id":3,"result":{"threadId":"thread-1","turn":{"id":"turn-1"}}}'
                  ${if (reasoningSummary) "printf '%s\\n' '{\"jsonrpc\":\"2.0\",\"method\":\"item/reasoning/summaryTextDelta\",\"params\":{\"delta\":\"planning\",\"itemId\":\"item-1\",\"summaryIndex\":0,\"threadId\":\"thread-1\",\"turnId\":\"turn-1\"}}'" else ""}
                  printf '%s\n' '{"jsonrpc":"2.0","id":99,"method":"item/tool/call","params":{"arguments":{},"callId":"call-1","threadId":"thread-1","tool":"context","turnId":"turn-1"}}'
                  IFS= read -r tool_response
                  $deltaEvents
                  printf '%s\n' '{"jsonrpc":"2.0","method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1"}}}'
                  ;;
              esac
            done
            """.trimIndent() + "\n",
        )
        check(executable.toFile().setExecutable(true))
        return executable
    }

    private fun deleteRecursively(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun responseText(response: ChatResponse): String =
        response.result
            ?.output
            ?.text
            .orEmpty()

    private fun deltaEvent(text: String): String = "printf '%s\\n' '${Json.encodeToString(JsonObject.serializer(), deltaMessage(text))}'"

    private fun deltaMessage(text: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive("item/agentMessage/delta"))
            put(
                "params",
                buildJsonObject {
                    put("delta", JsonPrimitive(text))
                    put("itemId", JsonPrimitive("item-1"))
                    put("threadId", JsonPrimitive("thread-1"))
                    put("turnId", JsonPrimitive("turn-1"))
                },
            )
        }
}
