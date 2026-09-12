package de.heckenmann.visualagent.ui.skills

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentActivity
import de.heckenmann.visualagent.protocol.SkillCreateResult
import de.heckenmann.visualagent.protocol.SkillDeleteResult
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.protocol.SkillPort
import de.heckenmann.visualagent.protocol.SkillSearchResult
import de.heckenmann.visualagent.protocol.SkillUpdateResult
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Verifies that the searchable skills panel opens complete documents from a row click. */
class ComposeSkillsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking a skill row requests its detail modal`() {
        val skill =
            SkillSearchResult(
                id = "skill-1",
                title = "Reusable build fix",
                snippet = "Use a pinned toolchain.",
                updatedAt = "2026-01-01T00:00:00Z",
                revision = 1,
                readCount = 0,
                lastReadAt = null,
            )
        val document = SkillDocument(skill, "# Reusable build fix\n\nUse a pinned toolchain.")
        val port = FakeSkillPort(skill, document)
        var requested: ComposeModal? = null

        composeTestRule.setContent {
            MaterialTheme {
                SkillsPanel(
                    skillPort = port,
                    activityPort = NoOpActivityPort,
                    modalRequester = ComposeModalRequester { requested = it },
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithText(skill.title).assertExists()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(skill.title).performClick()
        composeTestRule.waitUntil(5_000) { requested != null }

        val modal = assertIs<ComposeContentModal>(assertNotNull(requested))
        assertEquals(skill.title, modal.title)
        assertNotNull(port.lastRequestedId)
    }

    private class FakeSkillPort(
        private val skill: SkillSearchResult,
        private val document: SkillDocument,
    ) : SkillPort {
        var lastRequestedId: String? = null

        override fun search(
            query: String,
            limit: Int,
        ): List<SkillSearchResult> = listOf(skill)

        override fun get(id: String): SkillDocument? {
            lastRequestedId = id
            return document.takeIf { it.summary.id == id }
        }

        override fun create(
            title: String,
            content: String,
        ): SkillCreateResult = error("unused")

        override fun update(
            id: String,
            expectedRevision: Long,
            title: String,
            content: String,
        ): SkillUpdateResult = error("unused")

        override fun delete(
            id: String,
            expectedRevision: Long,
        ): SkillDeleteResult = error("unused")
    }

    private object NoOpActivityPort : ActivityPort {
        override fun addToolListener(listener: (ToolActivity) -> Unit): AutoCloseable = AutoCloseable {}

        override fun addAgentListener(listener: (AgentActivity) -> Unit): AutoCloseable = AutoCloseable {}
    }
}
