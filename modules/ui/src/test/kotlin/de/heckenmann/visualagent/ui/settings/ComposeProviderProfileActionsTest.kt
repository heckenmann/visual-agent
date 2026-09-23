package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/** Verifies that provider profile action buttons invoke their supplied handlers. */
class ComposeProviderProfileActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `add edit and remove buttons invoke their handlers`() {
        val addCalls = AtomicInteger()
        val editCalls = AtomicInteger()
        val removeCalls = AtomicInteger()
        composeTestRule.setContent {
            MaterialTheme {
                providerProfileActions(
                    canEdit = true,
                    canRemove = true,
                    onAdd = { addCalls.incrementAndGet() },
                    onEdit = { editCalls.incrementAndGet() },
                    onRemove = { removeCalls.incrementAndGet() },
                )
            }
        }

        composeTestRule.onNodeWithText("Add provider").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeTestRule.onNodeWithText("Edit provider").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeTestRule.onNodeWithText("Remove provider").performSemanticsAction(SemanticsActions.OnClick) { it() }

        composeTestRule.runOnIdle {
            assertEquals(1, addCalls.get())
            assertEquals(1, editCalls.get())
            assertEquals(1, removeCalls.get())
        }
    }
}
