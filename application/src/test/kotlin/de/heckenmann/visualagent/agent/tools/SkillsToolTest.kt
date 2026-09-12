package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies the model-facing skills tool uses structured actions and safe conflict results. */
@DatabaseTest
class SkillsToolTest {
    @Test
    fun `create search get update and delete are explicit json actions`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val tool = SkillsTool(SkillToolPortAdapter(db.skillStore))
            val markdown = "# Stored\n\n```kotlin\nval answer = 42\n```"
            val created =
                tool.execute(
                    """{"action":"create","title":"Stored","content":${Json.encodeToString(markdown)}}""",
                )
            val createdData = Json.parseToJsonElement(created.data.toString()).jsonObject
            val id = createdData["skill"]!!.jsonObject["id"]!!.jsonPrimitive.content
            val revision = createdData["skill"]!!.jsonObject["revision"]!!.jsonPrimitive.content

            assertTrue(created.success)
            assertEquals(markdown, db.skillStore.getSkill(id)?.content)
            assertTrue(tool.execute("""{"action":"search","query":"answer"}""").success)
            val document = tool.execute("""{"action":"get","id":"$id"}""")
            val documentData = document.data!!.jsonObject
            val documentContent = documentData["skill"]!!.jsonObject["content"]!!.jsonPrimitive.content
            assertEquals(markdown, documentContent)

            val updated =
                tool.execute(
                    """{"action":"update","id":"$id","expectedRevision":$revision,"title":"Updated","content":"# Updated"}""",
                )
            assertTrue(updated.success)
            val deleted =
                tool.execute(
                    """{"action":"delete","id":"$id","expectedRevision":${revision.toLong() + 1}}""",
                )
            assertTrue(deleted.success)
            assertEquals(null, db.skillStore.getSkill(id))
        }
    }

    @Test
    fun `tool returns actionable failures and does not expose markdown in lifecycle input`() {
        val port =
            object : de.heckenmann.visualagent.agent.tools.api.SkillToolPort {
                override fun create(
                    title: String,
                    content: String,
                ) = error("unused")

                override fun search(
                    query: String,
                    limit: Int,
                ) = emptyList<de.heckenmann.visualagent.agent.tools.api.ToolSkillSearchResult>()

                override fun read(
                    id: String,
                    isCancelled: () -> Boolean,
                ) = null

                override fun update(
                    id: String,
                    expectedRevision: Long,
                    title: String,
                    content: String,
                ) = error("unused")

                override fun delete(
                    id: String,
                    expectedRevision: Long,
                ) = error("unused")
            }
        val tool = SkillsTool(port)

        val invalid = tool.execute("""{"action":"unsupported","content":"private details"}""")
        assertFalse(invalid.success)
        assertNotNull(invalid.error)
        assertTrue(invalid.error!!.contains("Unsupported action"))
        assertTrue(tool.definition.description.contains("database records"))
        assertTrue(tool.definition.inputSchema.contains("expectedRevision"))
    }
}
