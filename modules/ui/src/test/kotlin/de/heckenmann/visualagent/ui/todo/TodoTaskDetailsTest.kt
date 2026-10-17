package de.heckenmann.visualagent.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskCheckState
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskCriterion
import de.heckenmann.visualagent.task.TaskObservation
import de.heckenmann.visualagent.task.TaskOutcome
import de.heckenmann.visualagent.task.WorkerHandoff
import org.junit.Rule
import org.junit.Test

class TodoTaskDetailsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `attempt details do not present unchecked worker claims as accepted`() {
        val contract = TaskContract(objective = "Grounded answer", criteria = listOf(TaskCriterion("fact", "Verify the fact")))
        val result =
            TaskAttemptResult(
                "todo",
                "worker",
                "execution",
                1,
                1,
                "worker/1",
                WorkerHandoff("Claims done", TaskOutcome.PRODUCED),
                emptyList(),
                listOf(TaskObservation("fact", TaskCheckState.NOT_CHECKED, "Missing evidence")),
            )
        rule.setContent { MaterialTheme { TodoTaskDetails(contract, listOf(result)) } }
        rule.onNodeWithText("Objective: Grounded answer").assertExists()
        rule.onNodeWithText("Attempt 1: Attempt only · PRODUCED").assertExists()
        rule.onNodeWithText("fact: NOT_CHECKED · Missing evidence").assertExists()
    }
}
