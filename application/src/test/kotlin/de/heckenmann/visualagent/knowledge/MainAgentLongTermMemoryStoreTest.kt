package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies durable main-agent memory persistence and optimistic replacement semantics. */
@DatabaseTest
class MainAgentLongTermMemoryStoreTest {
    @Test
    fun `memory starts empty and persists a caller supplied replacement with its revision`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val store = db.mainAgentLongTermMemoryStore

            val initial = store.snapshot()
            val saved = assertIs<MainAgentLongTermMemoryEdit.Saved>(store.replace("Plan the release", initial.revision, 12_000))

            assertEquals("Plan the release", saved.memory.content)
            assertEquals("Plan the release", store.snapshot().content)
            assertEquals(initial.revision + 1, saved.memory.revision)
            assertEquals("Plan the release".length, saved.memory.contentLength)
        }
    }

    @Test
    fun `stale revisions cannot overwrite the durable document`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val store = db.mainAgentLongTermMemoryStore
            val revision = store.snapshot().revision
            store.replace("first", revision, 12_000)

            val conflict = assertIs<MainAgentLongTermMemoryEdit.Conflict>(store.replace("stale", revision, 12_000))

            assertEquals("first", conflict.memory.content)
            assertEquals("first", store.snapshot().content)
        }
    }

    @Test
    fun `memory rejects oversized Unicode content without truncating it`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val store = db.mainAgentLongTermMemoryStore
            val oversized = "😀😀😀"

            val error = kotlin.test.assertFailsWith<IllegalArgumentException> { store.replace(oversized, 0, 2) }

            assertTrue(error.message!!.contains("maximum is 2"))
            assertEquals("", store.snapshot().content)
        }
    }
}
