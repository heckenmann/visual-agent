package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.heckenmann.visualagent.task.TaskContract
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoContractPolicyFieldsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `explicit empty tool grant denies access and unchecking restores inheritance`() {
        val contract = mutableStateOf(TaskContract(objective = "Bounded task"))
        rule.setContent {
            MaterialTheme {
                Column { TodoContractPolicyFields(contract.value) { contract.value = it } }
            }
        }
        val checkboxes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        checkboxes[0].performClick()
        rule.runOnIdle { assertEquals(emptyList(), contract.value.policy.tools) }
        rule
            .onNodeWithText("Allowed tool IDs (one per line; empty denies all)")
            .performTextReplacement("workspace:file")
        rule.runOnIdle { assertEquals(listOf("workspace:file"), contract.value.policy.tools) }
        checkboxes[0].performClick()
        rule.runOnIdle { assertNull(contract.value.policy.tools) }
        checkboxes[1].performClick()
        rule.runOnIdle { assertTrue(contract.value.policy.includeAgentHistory) }
    }
}
