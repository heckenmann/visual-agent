package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskCriterion
import de.heckenmann.visualagent.task.TaskDeliverable

/** Independent output/criterion fixture for structured planner regression tests. */
internal fun planningContract(objective: String): TaskContract =
    TaskContract(
        objective = objective,
        deliverables =
            listOf(
                TaskDeliverable("inspection", "Module report", "inspection.txt"),
                TaskDeliverable("pipeline", "Pipeline", "pipeline.txt"),
            ),
        criteria =
            listOf(
                TaskCriterion("inspect", "Inspect modules", "inspection"),
                TaskCriterion("implement", "Implement pipeline", "pipeline"),
            ),
    )

/** Schema-independent provider fallback still undergoes strict server validation. */
internal val independentPlan = """{
    "independent":true,
    "children":[
        {"description":"Inspect modules","criterionIds":["inspect"]},
        {"description":"Implement pipeline","criterionIds":["implement"]}
    ]
}"""
