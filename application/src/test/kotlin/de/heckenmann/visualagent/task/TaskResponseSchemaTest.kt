package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.orchestration.reviewTaskContract
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the production worker and reviewer schemas against strict structured-output requirements. */
class TaskResponseSchemaTest {
    @Test
    fun `worker schema requires every property and permits a null next action`() {
        val schema = Json.parseToJsonElement(WorkerHandoff.schema()).jsonObject
        assertStrictSchema(schema)
        assertEquals(
            setOf("string", "null"),
            schema
                .getValue("properties")
                .jsonObject
                .getValue("nextAction")
                .jsonObject
                .getValue("type")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet(),
        )
        val handoff =
            WorkerHandoff.parse(
                """{"summary":"Done","outcome":"PRODUCED","artifacts":[],"sources":[],"blockers":[],"nextAction":null}""",
                TaskContract(objective = "Reply"),
            )
        assertNull(handoff.nextAction)
        assertEquals(TaskOutcome.PRODUCED, handoff.outcome)
    }

    @Test
    fun `review request uses a strict schema with explicitly typed verdict and observation state`() {
        val provider = mockk<LLMProvider>()
        var requests = 0
        every { provider.chatReactive(any<ChatRequestContext>()) } answers {
            requests++
            val request = firstArg<ChatRequestContext>()
            assertStrictSchema(Json.parseToJsonElement(assertNotNull(request.responseSchema).json).jsonObject)
            Mono.just(ChatResponse("review", Message("assistant", """{"verdict":"APPROVED","feedback":"Checked","criteria":[]}"""), true))
        }
        val contract = TaskContract(objective = "Reply")
        val result =
            TaskAttemptResult(
                "todo",
                "worker",
                "execution",
                1,
                1,
                "worker/1",
                WorkerHandoff("Done", TaskOutcome.PRODUCED),
                emptyList(),
                emptyList(),
            )
        assertNotNull(reviewTaskContract(provider, contract, result, TaskReviewEvidence("", emptyMap()), null, CancellationToken()).block())
        assertEquals(1, requests)
    }

    private fun assertStrictSchema(schema: JsonObject) {
        val type = assertNotNull(schema["type"], "Every schema node needs an explicit type")
        val types = if (type is JsonArray) type.map { it.jsonPrimitive.content } else listOf(type.jsonPrimitive.content)
        assertTrue(types.isNotEmpty())
        if ("object" in types) {
            val properties = schema.getValue("properties").jsonObject
            assertEquals(
                properties.keys,
                schema
                    .getValue("required")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet(),
            )
            assertEquals("false", schema.getValue("additionalProperties").jsonPrimitive.content)
            properties.values.forEach { assertStrictSchema(it.jsonObject) }
        }
        if ("array" in types) assertStrictSchema(schema.getValue("items").jsonObject)
    }
}
