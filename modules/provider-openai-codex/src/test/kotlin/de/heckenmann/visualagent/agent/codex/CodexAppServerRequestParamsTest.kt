package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies role-preserving request mapping for the Codex app-server protocol. */
class CodexAppServerRequestParamsTest {
    @Test
    fun `history is contextualized while the latest user request remains the current turn`() {
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
        val developerInstructions = thread.getValue("developerInstructions").jsonPrimitive.content
        val providerConfig = thread.getValue("config").jsonObject
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
        assertTrue(developerInstructions.contains("[user]\nold request"))
        assertTrue(developerInstructions.contains("[assistant]\nold response"))
        assertTrue(developerInstructions.contains("context only"))
        assertFalse(developerInstructions.contains("say hello to me"))
        assertEquals(1, inputs.size)
        assertEquals(
            "say hello to me",
            inputs
                .single()
                .jsonObject
                .getValue("text")
                .jsonPrimitive
                .content,
        )
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
}
