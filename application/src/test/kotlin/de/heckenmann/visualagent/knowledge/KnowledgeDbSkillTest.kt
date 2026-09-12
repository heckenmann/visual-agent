package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies durable skill validation, indexing, idempotency, and optimistic concurrency. */
@DatabaseTest
class KnowledgeDbSkillTest {
    @Test
    fun `create preserves markdown and read records model access`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val markdown = "# Reusable result\n\n```kotlin\nfun answer() = 42\n```"
            val created = assertIs<SkillCreateResult.Created>(db.skillStore.createSkill("Reusable result", markdown))

            assertEquals(markdown, db.skillStore.getSkill(created.skill.id)?.content)
            assertEquals(0, created.skill.readCount)

            val read = assertNotNull(db.skillStore.readSkill(created.skill.id))
            assertEquals(markdown, read.content)
            assertEquals(1, read.readCount)
            assertNotNull(read.lastReadAt)
        }
    }

    @Test
    fun `exact duplicate returns the existing skill instead of creating another row`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val first = assertIs<SkillCreateResult.Created>(db.skillStore.createSkill("Title", "# Body"))
            val duplicate = assertIs<SkillCreateResult.Duplicate>(db.skillStore.createSkill(" Title ", "# Body"))

            assertEquals(first.skill.id, duplicate.skill.id)
            assertEquals(1, db.skillStore.searchSkills("", 25).size)
        }
    }

    @Test
    fun `fts search is updated atomically and stale revisions cannot overwrite`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val created =
                assertIs<SkillCreateResult.Created>(
                    db.skillStore.createSkill("SQLite indexing", "Use FTS5 for durable lookup."),
                )
            assertTrue(db.skillStore.searchSkills("FTS5", 5).any { it.id == created.skill.id })

            val updated =
                assertIs<SkillUpdateResult.Updated>(
                    db.skillStore.updateSkill(
                        created.skill.id,
                        created.skill.revision,
                        "SQLite migration",
                        "Use triggers for atomic indexing.",
                    ),
                )
            assertFalse(db.skillStore.searchSkills("FTS5", 5).any { it.id == created.skill.id })
            assertTrue(db.skillStore.searchSkills("triggers", 5).any { it.id == created.skill.id })

            val conflict =
                assertIs<SkillUpdateResult.Conflict>(
                    db.skillStore.updateSkill(created.skill.id, created.skill.revision, "Stale", "Must not overwrite."),
                )
            assertEquals(updated.skill.revision, conflict.skill.revision)
            assertEquals("SQLite migration", conflict.skill.title)
        }
    }

    @Test
    fun `delete requires current revision and removes indexed result`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            val created =
                assertIs<SkillCreateResult.Created>(
                    db.skillStore.createSkill("Disposable", "Remove this result."),
                )
            val conflict =
                assertIs<SkillDeleteResult.Conflict>(
                    db.skillStore.deleteSkill(created.skill.id, created.skill.revision + 1),
                )
            assertEquals(created.skill.id, conflict.skill.id)

            val deleted = assertIs<SkillDeleteResult.Deleted>(db.skillStore.deleteSkill(created.skill.id, created.skill.revision))
            assertEquals(created.skill.id, deleted.id)
            assertEquals(null, db.skillStore.getSkill(created.skill.id))
            assertTrue(db.skillStore.searchSkills("Disposable", 5).none { it.id == created.skill.id })
            assertIs<SkillDeleteResult.NotFound>(db.skillStore.deleteSkill(created.skill.id, created.skill.revision))
        }
    }

    @Test
    fun `blank values and oversized search queries are rejected`() {
        KnowledgeDbTestFactory.create("jdbc:sqlite::memory:").use { db ->
            kotlin.test.assertFailsWith<IllegalArgumentException> { db.skillStore.createSkill(" ", "content") }
            kotlin.test.assertFailsWith<IllegalArgumentException> { db.skillStore.createSkill("title", " ") }
            kotlin.test.assertFailsWith<IllegalArgumentException> { db.skillStore.searchSkills("x".repeat(501), 5) }
        }
    }
}
