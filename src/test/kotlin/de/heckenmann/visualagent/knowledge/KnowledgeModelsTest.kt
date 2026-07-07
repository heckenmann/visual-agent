package de.heckenmann.visualagent.knowledge

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnowledgeModelsTest {
    @Test
    fun `memory equality depends only on id`() {
        val a = Memory("id-1", "content a", listOf("tag"), Instant.now(), byteArrayOf(1, 2, 3))
        val b = Memory("id-1", "content b", listOf("other"), Instant.EPOCH, byteArrayOf(4, 5, 6))
        val c = Memory("id-2", "content a", listOf("tag"), Instant.now(), byteArrayOf(1, 2, 3))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertFalse(a.equals("not a memory"))
        assertTrue(a.equals(a))
    }

    @Test
    fun `memory copy preserves id based equality`() {
        val original = Memory("id-3", "original", emptyList(), Instant.now())
        val copy = original.copy(content = "changed")

        assertEquals(original, copy)
    }

    @Test
    fun `project knowledge holds fields`() {
        val now = Instant.now()
        val pk = ProjectKnowledge("pk-1", "/path", "name", "desc", "summary", now)

        assertEquals("pk-1", pk.id)
        assertEquals("/path", pk.projectPath)
        assertEquals("name", pk.name)
        assertEquals("desc", pk.description)
        assertEquals("summary", pk.summary)
        assertEquals(now, pk.lastAccessed)
    }
}

class PersistenceStoresModelsTest {
    @Test
    fun `conversation record supports field access by name`() {
        val now = Instant.now()
        val record = ConversationRecord("r-1", "user", "hello", "{}", now)

        assertEquals("r-1", record["id"])
        assertEquals("user", record["role"])
        assertEquals("hello", record["content"])
        assertEquals("{}", record["metadata"])
        assertEquals(now.toString(), record["createdAt"])
        assertNull(record["unknown"])
    }

    @Test
    fun `persisted sub agent supports field access by name`() {
        val created = Instant.now()
        val updated = created.plusMillis(1)
        val agent =
            PersistedSubAgent(
                id = "a-1",
                name = "Worker",
                role = "coder",
                status = "IDLE",
                currentTask = "task",
                parentAgentId = "main",
                config = "{}",
                createdAt = created,
                updatedAt = updated,
            )

        assertEquals("a-1", agent["id"])
        assertEquals("Worker", agent["name"])
        assertEquals("coder", agent["role"])
        assertEquals("IDLE", agent["status"])
        assertEquals("task", agent["currentTask"])
        assertEquals("main", agent["parentAgentId"])
        assertEquals("{}", agent["config"])
        assertEquals(created.toString(), agent["createdAt"])
        assertEquals(updated.toString(), agent["updatedAt"])
        assertNull(agent["unknown"])
    }
}
