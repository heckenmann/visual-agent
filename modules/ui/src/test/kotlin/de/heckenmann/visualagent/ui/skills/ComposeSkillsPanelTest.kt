@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.skills

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

    @Test
    fun `duplicate save feedback is visible inside the editor`() {
        composeTestRule.setContent {
            MaterialTheme {
                SkillEditor(
                    title = "Existing skill",
                    content = "# Existing skill",
                    isNew = false,
                    conflict = null,
                    saveError = "An equivalent skill already exists: Existing skill.",
                    onTitleChanged = {},
                    onContentChanged = {},
                    onCancel = {},
                    onReset = {},
                    onSave = {},
                    onReload = {},
                    onKeepDraft = {},
                    onSaveAsNew = {},
                    onDiscard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("An equivalent skill already exists: Existing skill.").assertExists()
    }

    @Test
    fun `skills panel reports overlong search queries`() {
        val skill = SkillSearchResult("skill-1", "Reusable", "excerpt", "2026-01-01", 1, 0, null)
        val port = FakeSkillPort(skill, SkillDocument(skill, "Body"))
        composeTestRule.setContent {
            MaterialTheme {
                SkillsPanel(port, NoOpActivityPort, ComposeModalRequester {})
            }
        }
        composeTestRule.onNodeWithText("Search skills").performTextInput("x".repeat(MAX_QUERY_CODE_POINTS + 1))
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithText("Search query is limited to $MAX_QUERY_CODE_POINTS Unicode code points.").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun `skills panel reports duplicate creates inside the open editor`() {
        val skill = SkillSearchResult("skill-1", "Existing", "excerpt", "2026-01-01", 1, 0, null)
        val port = FakeSkillPort(skill, SkillDocument(skill, "Existing body"))
        port.createResult = SkillCreateResult.Duplicate(skill)
        val requested = mutableStateOf<ComposeModal?>(null)
        composeTestRule.setContent {
            MaterialTheme {
                SkillsPanel(port, NoOpActivityPort, ComposeModalRequester { requested.value = it })
                (requested.value as? ComposeContentModal)?.content {}
            }
        }

        composeTestRule.waitUntil(5_000) { composeTestRule.onNodeWithText(skill.title).isDisplayed() }
        composeTestRule.onNodeWithContentDescription("Create skill").performClick()
        composeTestRule.onNodeWithText("Title").performTextInput("Duplicate")
        composeTestRule.onNodeWithText("Markdown").performTextInput("Duplicate body")
        composeTestRule.onNodeWithText("Create skill").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithText("An equivalent skill already exists: Existing.").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun `skills panel opens created skill after a successful save`() {
        val original = SkillSearchResult("skill-1", "Existing", "excerpt", "2026-01-01", 1, 0, null)
        val created = SkillSearchResult("skill-2", "Created", "new excerpt", "2026-01-02", 1, 0, null)
        val port = FakeSkillPort(original, SkillDocument(original, "Existing body"))
        port.createResult = SkillCreateResult.Created(created)
        port.document = SkillDocument(created, "Created body")
        val requested = mutableStateOf<ComposeModal?>(null)
        composeTestRule.setContent {
            MaterialTheme {
                SkillsPanel(port, NoOpActivityPort, ComposeModalRequester { requested.value = it })
                (requested.value as? ComposeContentModal)?.content {}
            }
        }

        composeTestRule.waitUntil(5_000) { composeTestRule.onNodeWithText(original.title).isDisplayed() }
        composeTestRule.onNodeWithContentDescription("Create skill").performClick()
        composeTestRule.onNodeWithText("Title").performTextInput("Created")
        composeTestRule.onNodeWithText("Markdown").performTextInput("Created body")
        composeTestRule.onNodeWithText("Create skill").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithText("Revision 1 · 0 model reads").assertExists()
                true
            }.getOrDefault(false)
        }
        kotlin.test.assertEquals("skill-2", port.lastRequestedId)
    }

    @Test
    fun `skills panel handles missing selected skill and search failure`() {
        val skill = SkillSearchResult("skill-1", "Missing", "excerpt", "2026-01-01", 1, 0, null)
        val port = FakeSkillPort(skill, SkillDocument(skill, "Body"))
        val requested = mutableStateOf<ComposeModal?>(null)
        composeTestRule.setContent {
            MaterialTheme {
                SkillsPanel(port, NoOpActivityPort, ComposeModalRequester { requested.value = it })
            }
        }
        composeTestRule.waitUntil(5_000) { composeTestRule.onNodeWithText(skill.title).isDisplayed() }
        port.document = null
        composeTestRule.onNodeWithText(skill.title).performClick()
        composeTestRule.waitForIdle()
        port.failSearch = true
        composeTestRule.onNodeWithContentDescription("Refresh skills").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithText("Unable to load skills: search failed").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private class FakeSkillPort(
        private val skill: SkillSearchResult,
        var document: SkillDocument?,
    ) : SkillPort {
        var lastRequestedId: String? = null
        var createResult: SkillCreateResult? = null
        var updateResult: SkillUpdateResult? = null
        var deleteResult: SkillDeleteResult? = null
        var failSearch = false

        override fun search(
            query: String,
            limit: Int,
        ): List<SkillSearchResult> {
            check(!failSearch) { "search failed" }
            return listOf(skill)
        }

        override fun get(id: String): SkillDocument? {
            lastRequestedId = id
            return document?.takeIf { it.summary.id == id }
        }

        override fun create(
            title: String,
            content: String,
        ): SkillCreateResult = createResult ?: error("unused")

        override fun update(
            id: String,
            expectedRevision: Long,
            title: String,
            content: String,
        ): SkillUpdateResult = updateResult ?: error("unused")

        override fun delete(
            id: String,
            expectedRevision: Long,
        ): SkillDeleteResult = deleteResult ?: error("unused")
    }

    private object NoOpActivityPort : ActivityPort {
        override fun addToolListener(listener: (ToolActivity) -> Unit): AutoCloseable = AutoCloseable {}

        override fun addAgentListener(listener: (AgentActivity) -> Unit): AutoCloseable = AutoCloseable {}
    }
}
