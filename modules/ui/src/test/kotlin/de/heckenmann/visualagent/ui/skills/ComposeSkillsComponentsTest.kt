@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.skills

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.protocol.SkillSearchResult
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the standalone skills catalog components and their user actions. */
class ComposeSkillsComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `skill detail exposes copy edit delete and close actions`() {
        val document =
            SkillDocument(
                SkillSearchResult("skill-1", "Reusable", "excerpt", "2026-01-01", 3, 2, "2026-01-02"),
                "# Reusable\n\nUse this skill.",
            )
        var copied = false
        var edited = false
        var deleted = false
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                SkillDetail(
                    document,
                    onEdit = { edited = true },
                    onCopy = { copied = true },
                    onDelete = { deleted = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Revision 3 · 2 model reads · last read 2026-01-02").assertExists()
        composeTestRule.onNodeWithText("Copy Markdown").performClick()
        composeTestRule.onNodeWithText("Edit skill").performClick()
        composeTestRule.onNodeWithText("Delete skill").performClick()
        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(copied && edited && deleted && dismissed)
    }

    @Test
    fun `skill detail omits optional last-read metadata`() {
        val document =
            SkillDocument(
                SkillSearchResult("skill-1", "Reusable", "excerpt", "2026-01-01", 1, 0, null),
                "Reusable",
            )
        composeTestRule.setContent {
            MaterialTheme {
                SkillDetail(document, {}, {}, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Revision 1 · 0 model reads").assertExists()
    }

    @Test
    fun `editor renders changed and deleted conflict actions`() {
        val skill = SkillSearchResult("skill-1", "Reusable", "excerpt", "2026-01-01", 4, 0, null)
        var actionCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                SkillEditor(
                    title = "Draft",
                    content = "Body",
                    isNew = false,
                    conflict = SkillConflict.Changed(skill),
                    saveError = null,
                    onTitleChanged = {},
                    onContentChanged = {},
                    onCancel = { actionCount++ },
                    onReset = { actionCount++ },
                    onSave = {},
                    onReload = { actionCount++ },
                    onKeepDraft = { actionCount++ },
                    onSaveAsNew = {},
                    onDiscard = {},
                )
            }
        }
        composeTestRule
            .onNodeWithText(
                "This skill changed elsewhere (revision 4). Reload the stored version or keep this draft.",
            ).assertExists()
        composeTestRule.onNodeWithText("Reload stored version").performClick()
        composeTestRule.onNodeWithText("Keep draft").performClick()

        composeTestRule.setContent {
            MaterialTheme {
                SkillEditor(
                    title = "Draft",
                    content = "Body",
                    isNew = false,
                    conflict = SkillConflict.Deleted,
                    saveError = null,
                    onTitleChanged = {},
                    onContentChanged = {},
                    onCancel = {},
                    onReset = {},
                    onSave = {},
                    onReload = {},
                    onKeepDraft = {},
                    onSaveAsNew = { actionCount++ },
                    onDiscard = { actionCount++ },
                )
            }
        }
        composeTestRule.onNodeWithText("This skill was deleted elsewhere. Save this draft as a new skill or discard it.").assertExists()
        composeTestRule.onNodeWithText("Save as new").performClick()
        composeTestRule.onNodeWithText("Discard draft").performClick()
        assertEquals(4, actionCount)
    }

    @Test
    fun `skills list renders empty state`() {
        composeTestRule.setContent {
            MaterialTheme {
                SkillsCatalogList(emptyList(), null, onSelect = {})
            }
        }
        composeTestRule.onNodeWithText("No skills found").assertExists()
    }

    @Test
    fun `skills toolbar invokes search refresh and create actions`() {
        var searches = 0
        var creates = 0
        composeTestRule.setContent {
            MaterialTheme {
                SkillsToolbar("", {}, { searches++ }, { creates++ })
            }
        }
        composeTestRule.onNodeWithContentDescription("Search skills").performClick()
        composeTestRule.onNodeWithContentDescription("Refresh skills").performClick()
        composeTestRule.onNodeWithContentDescription("Create skill").performClick()
        assertEquals(2, searches)
        assertEquals(1, creates)
    }
}
