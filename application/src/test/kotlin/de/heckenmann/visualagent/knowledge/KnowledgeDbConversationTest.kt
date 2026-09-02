package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.todo.Todo
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class KnowledgeDbConversationTest {
    @Test
    fun `caller supplied message identity is insert only and idempotent`() {
        val tempDb = createTempDirectory("visual-agent-conversation-id-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val id = "11111111-1111-4111-8111-111111111111"

        assertEquals(id, db.conversationStore.saveConversationMessage(id, "main", "user", "Hello"))
        val sequence = db.conversationStore.getConversationMessage(id)!!.timelineSequence
        assertEquals(id, db.conversationStore.saveConversationMessage(id, "main", "user", "Hello"))
        assertEquals(sequence, db.conversationStore.getConversationMessage(id)!!.timelineSequence)
        assertFailsWith<IllegalArgumentException> {
            db.conversationStore.saveConversationMessage(id, "main", "assistant", "Changed")
        }
        db.close()
    }

    @Test
    fun `save load and delete conversation messages`() {
        val tempDb = createTempDirectory("visual-agent-conversation-db-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val sessionId = "main"

        db.saveConversationMessage(sessionId, "user", "Hello")
        db.saveConversationMessage(sessionId, "assistant", "Hi there")
        db.saveConversationMessage("other", "user", "Ignore me")

        val messages = db.getConversationMessages(sessionId)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0]["role"])
        assertEquals("Hello", messages[0]["content"])
        assertEquals("assistant", messages[1]["role"])
        assertEquals("Hi there", messages[1]["content"])

        val deleted = db.deleteConversationMessages(sessionId)
        assertTrue(deleted >= 2)
        assertEquals(0, db.getConversationMessages(sessionId).size)
        assertEquals(1, db.getConversationMessages("other").size)
        db.close()
    }

    @Test
    fun `conversation supports paging and keyword search`() {
        val tempDb = createTempDirectory("visual-agent-conversation-page-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val sessionId = "main"
        repeat(10) { idx ->
            db.saveConversationMessage(sessionId, if (idx % 2 == 0) "user" else "assistant", "entry-$idx keyword")
        }

        val latestPage = db.getConversationMessagesPage(sessionId, limit = 3, offset = 0)
        assertEquals(3, latestPage.size)
        assertEquals("entry-7 keyword", latestPage[0]["content"])
        assertEquals("entry-9 keyword", latestPage[2]["content"])

        val olderPage = db.getConversationMessagesPage(sessionId, limit = 4, offset = 6)
        assertEquals(4, olderPage.size)
        assertEquals("entry-0 keyword", olderPage[0]["content"])
        assertEquals("entry-3 keyword", olderPage[3]["content"])

        val matches = db.searchConversationMessages(sessionId, "entry-8", limit = 5)
        assertEquals(1, matches.size)
        assertEquals("entry-8 keyword", matches[0]["content"])
        db.close()
    }

    @Test
    fun `conversation messages receive database timeline sequence values`() {
        val tempDb = createTempDirectory("visual-agent-conversation-sequence-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)

        db.saveConversationMessage("main", "user", "First")
        db.saveConversationMessage("main", "assistant", "Second")

        val messages = db.getConversationMessages("main")
        assertTrue(messages[0].timelineSequence > 0)
        assertTrue(messages[1].timelineSequence > messages[0].timelineSequence)
        db.close()
    }

    @Test
    fun `main context query keeps recent turns and excludes audit-only records`() {
        val tempDb = createTempDirectory("visual-agent-conversation-context-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val ids = (1..4).map { "11111111-1111-4111-8111-11111111111$it" }

        db.conversationStore.saveConversationMessage(ids[0], "main", "user", "Old request", null, ConversationContextPolicy.DIALOGUE)
        db.conversationStore.saveConversationMessage(ids[1], "main", "assistant", "Old answer", null, ConversationContextPolicy.DIALOGUE)
        db.conversationStore.saveConversationMessage(ids[2], "main", "system", "Internal trace", null, ConversationContextPolicy.AUDIT_ONLY)
        db.conversationStore.saveConversationMessage(ids[3], "main", "user", "Current request", null, ConversationContextPolicy.DIALOGUE)

        val context = db.conversationStore.getConversationMessagesForContext("main", userTurnLimit = 1, recordLimit = 20)

        assertEquals(listOf("Current request"), context.filter { it.role == "user" }.map { it.content })
        assertTrue(context.none { it.content == "Internal trace" })
        db.close()
    }

    @Test
    fun `main context query retains the current dialogue when execution events exceed the record budget`() {
        val tempDb = createTempDirectory("visual-agent-conversation-context-budget-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val userId = "22222222-2222-4222-8222-222222222222"
        db.conversationStore.saveConversationMessage(
            userId,
            "main",
            "user",
            "Current request",
            null,
            ConversationContextPolicy.DIALOGUE,
        )
        repeat(25) { index ->
            db.conversationStore.saveConversationMessage(
                java
                    .util.UUID
                    .randomUUID()
                    .toString(),
                "main",
                "system",
                "Execution event $index",
                """{"type":"tool_call","toolId":"tool-$index"}""",
                ConversationContextPolicy.SUMMARY_SOURCE,
            )
        }

        val context = db.conversationStore.getConversationMessagesForContext("main", userTurnLimit = 1, recordLimit = 5)

        assertTrue(context.any { it.id == userId })
        assertTrue(context.any { it.content == "Execution event 24" })
        db.close()
    }

    @Test
    fun `main context query includes summary events that immediately precede the selected user turn`() {
        val tempDb = createTempDirectory("visual-agent-conversation-prelude-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        db.conversationStore.saveConversationMessage(
            "33333333-3333-4333-8333-333333333333",
            "main",
            "system",
            "Workspace file imported: input.csv.",
            """{"type":"workspace_file","workspacePath":"imports/input.csv","operation":"import"}""",
            ConversationContextPolicy.SUMMARY_SOURCE,
        )
        db.conversationStore.saveConversationMessage(
            "44444444-4444-4444-8444-444444444444",
            "main",
            "user",
            "Analyze the imported file",
            null,
            ConversationContextPolicy.DIALOGUE,
        )

        val context = db.conversationStore.getConversationMessagesForContext("main", userTurnLimit = 1, recordLimit = 20)

        assertTrue(context.any { it.content == "Workspace file imported: input.csv." })
        assertTrue(context.any { it.content == "Analyze the imported file" })
        db.close()
    }

    @Test
    fun `main context query remains bounded when legacy timeline values are zero`() {
        val tempDb = createTempDirectory("visual-agent-conversation-legacy-sequence-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        repeat(12) { index ->
            db.saveConversationMessage("main", "user", "Request $index")
            db.saveConversationMessage("main", "assistant", "Answer $index")
        }
        DriverManager.getConnection("jdbc:sqlite:$tempDb").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE conversation_history SET timeline_sequence = 0")
            }
        }

        val context = db.conversationStore.getConversationMessagesForContext("main", userTurnLimit = 1, recordLimit = 20)

        assertEquals(listOf("Request 11"), context.filter { it.role == "user" }.map { it.content })
        assertEquals(listOf("Answer 11"), context.filter { it.role == "assistant" }.map { it.content })
        db.close()
    }

    @Test
    fun `todo activity shares the conversation ordering sequence`() {
        val tempDb = createTempDirectory("visual-agent-todo-sequence-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)

        db.saveConversationMessage("main", "user", "Create a task")
        val message = db.getConversationMessages("main").single()
        val todo = Todo("todo", "Task")
        db.todoStore.saveTodo(todo)

        assertTrue(todo.timelineSequence > message.timelineSequence)
        val firstActivity = todo.timelineSequence
        db.todoStore.saveTodo(todo)
        assertTrue(todo.timelineSequence > firstActivity)
        db.close()
    }

    @Test
    fun `todo reordering preserves existing conversation timeline activity`() {
        val tempDb =
            createTempDirectory("visual-agent-todo-reorder-sequence-test")
                .resolve("history.db")
                .toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val first = Todo("first", "First", position = 0)
        val second = Todo("second", "Second", position = 1)

        db.todoStore.saveTodo(first)
        db.todoStore.saveTodo(second)
        db.saveConversationMessage("main", "user", "Message after todo creation")
        val sequencesBeforeReorder = db.todoStore.listTodos().associate { it.id to it.timelineSequence }
        val followingMessage = db.getConversationMessages("main").single()

        second.position = 0
        first.position = 1
        db.todoStore.updateTodoPositions(listOf(second, first))

        val reordered = db.todoStore.listTodos()
        assertEquals(listOf("second", "first"), reordered.map { it.id })
        assertEquals(sequencesBeforeReorder, reordered.associate { it.id to it.timelineSequence })
        assertTrue(reordered.all { it.timelineSequence < followingMessage.timelineSequence })
        db.close()
    }
}
