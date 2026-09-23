package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies role-preserving request mapping for the Codex app-server protocol. */
class CodexAppServerRequestParamsTest {
    @Test
    fun `completed conversation turns retain native message roles`() {
        val prompt =
            Prompt(
                listOf(
                    SystemMessage("system rules"),
                    UserMessage("old request"),
                    AssistantMessage("old response"),
                    UserMessage("say hello to me"),
                ),
            )

        val thread = CodexAppServerRequestParams.thread(prompt, "model", Path.of("."), emptyList())
        val baseInstructions = thread.getValue("baseInstructions").jsonPrimitive.content
        val providerConfig = thread.getValue("config").jsonObject
        val history = CodexAppServerRequestParams.historyItems(prompt)
        val turn = CodexAppServerRequestParams.turn(prompt, "thread", "model", false, null)
        val inputs = turn.getValue("input").jsonArray

        assertEquals(
            0,
            providerConfig
                .getValue("project_doc_max_bytes")
                .jsonPrimitive
                .content
                .toInt(),
        )
        assertEquals("system rules", baseInstructions)
        assertFalse(
            providerConfig
                .getValue("include_apps_instructions")
                .jsonPrimitive
                .content
                .toBoolean(),
        )
        assertFalse(
            providerConfig
                .getValue("include_collaboration_mode_instructions")
                .jsonPrimitive
                .content
                .toBoolean(),
        )
        assertFalse("developerInstructions" in thread)
        assertEquals(2, history.size)
        assertEquals(
            listOf("user", "assistant"),
            history.map {
                it.jsonObject
                    .getValue("role")
                    .jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("input_text", "output_text"),
            history.map {
                it.jsonObject
                    .getValue("content")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("type")
                    .jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("old request", "old response"),
            history.map {
                it.jsonObject
                    .getValue("content")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("text")
                    .jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("say hello to me"),
            inputs.map {
                it.jsonObject
                    .getValue("text")
                    .jsonPrimitive.content
            },
        )
    }

    @Test
    fun `history injection is scoped to the started thread`() {
        val items = CodexAppServerRequestParams.historyItems(Prompt(UserMessage("earlier")))
        val params = CodexAppServerRequestParams.injectHistory("thread-1", items)

        assertEquals("thread-1", params.getValue("threadId").jsonPrimitive.content)
        assertEquals(items, params.getValue("items"))
    }

    @Test
    fun `messages added after the latest user request stay in the active turn`() {
        val prompt =
            Prompt(
                listOf(
                    SystemMessage("system rules"),
                    UserMessage("complete the result"),
                    AssistantMessage("tool results"),
                ),
            )

        val turn = CodexAppServerRequestParams.turn(prompt, "thread", "model", false, null)
        val texts =
            turn.getValue("input").jsonArray.map {
                it.jsonObject
                    .getValue("text")
                    .jsonPrimitive
                    .content
            }

        assertEquals(listOf("complete the result", "[assistant]\ntool results"), texts)
    }

    @Test
    fun `thread requests are ephemeral and sandboxed without implicit collaboration context`() {
        val thread =
            CodexAppServerRequestParams.thread(
                Prompt(UserMessage("request")),
                "model",
                Path.of("workspace"),
                emptyList(),
            )

        assertEquals(
            true,
            thread
                .getValue("ephemeral")
                .jsonPrimitive
                .content
                .toBoolean(),
        )
        assertEquals("read-only", thread.getValue("sandbox").jsonPrimitive.content)
        assertEquals("never", thread.getValue("approvalPolicy").jsonPrimitive.content)
        assertEquals(
            false,
            thread
                .getValue("config")
                .jsonObject
                .getValue("include_collaboration_mode_instructions")
                .jsonPrimitive
                .content
                .toBoolean(),
        )
        assertTrue("dynamicTools" in thread)
    }

    @Test
    fun `dynamic tool schema preserves the provider function name`() {
        val callback =
            object : ToolCallback {
                override fun getToolDefinition(): ToolDefinition =
                    ToolDefinition
                        .builder()
                        .name("agent_list")
                        .description("Lists agents")
                        .inputSchema("{}")
                        .build()

                override fun call(toolInput: String): String = "{}"
            }

        val thread = CodexAppServerRequestParams.thread(Prompt(UserMessage("request")), "model", Path.of("workspace"), listOf(callback))
        val functionName =
            thread
                .getValue("dynamicTools")
                .jsonArray
                .single()
                .jsonObject
                .getValue("name")
                .jsonPrimitive.content

        assertEquals("agent_list", functionName)
        assertFalse(functionName.contains(':'))
    }
}
