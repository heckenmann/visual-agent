package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import reactor.core.publisher.Mono

/** Per-execution authority and evidence, separate from the coordinator's lifecycle state. */
internal class TaskAttemptSession(
    private val agent: SubAgent,
    private val claimedTodo: Todo,
    private val todoManager: TodoManager,
    private val agentToolConfigService: AgentToolConfigService,
    private val taskPlanner: AutonomousTaskPlanner,
    private val token: CancellationToken,
    private val taskExecutionService: de.heckenmann.visualagent.task.TaskExecutionService?,
    private val taskDescription: String,
) {
    var result: de.heckenmann.visualagent.task.TaskAttemptResult? = null
        private set

    private val inputVersions by lazy {
        claimedTodo.contract?.let { taskExecutionService?.inputVersions(it) }.orEmpty()
    }

    fun instruction(feedback: String?): String =
        taskDescription +
            feedback
                ?.let {
                    "\n\nReview correction: $it\n" +
                        result
                            ?.observations
                            ?.filter { observation ->
                                observation.state != de.heckenmann.visualagent.task.TaskCheckState.PASSED
                            }?.joinToString("\n") { observation ->
                                "${observation.criterionId}: ${observation.state}: ${observation.detail}"
                            }.orEmpty()
                            .take(8_192) + "\n" +
                        "Inspect prior work; correct the missing parts without repeating successful side effects."
                }.orEmpty()

    fun enabledTools(): Set<de.heckenmann.visualagent.agent.ToolId> =
        agentToolConfigService
            .toolsFor(agent)
            .filter { tool ->
                claimedTodo.contract
                    ?.policy
                    ?.tools
                    ?.let { tool.value in it } ?: true
            }.filter { tool ->
                de.heckenmann.visualagent.agent.tools.TaskToolAccess.supportsResourceScope(
                    tool.value,
                    claimedTodo.contract?.policy?.resources,
                )
            }.toSet()

    fun needsIntervention(): Boolean {
        val captured = result ?: return false
        val required =
            claimedTodo.contract
                ?.criteria
                ?.filter { it.required }
                .orEmpty()
                .map { it.id }
                .toSet()
        return claimedTodo.contract?.let { taskExecutionService?.inputsCurrent(it, captured.inputVersions) == false } == true ||
            captured.reported.outcome == de.heckenmann.visualagent.task.TaskOutcome.BLOCKED ||
            captured.observations.any {
                it.criterionId in required && it.state == de.heckenmann.visualagent.task.TaskCheckState.NOT_CHECKED
            }
    }

    fun canAccept(): Boolean = claimedTodo.contract == null || result?.let { taskExecutionService?.canAccept(claimedTodo, it) } == true

    fun metadata(): Map<String, Any> {
        val contract = claimedTodo.contract ?: return emptyMap()
        inputVersions
        val access =
            de.heckenmann.visualagent.agent.tools.TaskToolAccess(
                contract.policy,
                { agentToolConfigService.toolsFor(agent).map { it.value }.toSet() },
                { !token.isCancelled && todoManager.getById(claimedTodo.id)?.copy(position = claimedTodo.position) == claimedTodo },
                { taskExecutionService?.resourceAllowed(contract, it) == true },
            )
        return mapOf(
            "taskToolAccess" to access,
            "excludeAgentHistory" to !contract.policy.includeAgentHistory,
            "taskResponseSchema" to
                de.heckenmann.visualagent.agent
                    .ResponseSchema(
                        de.heckenmann.visualagent.task.WorkerHandoff
                            .schema(),
                    ),
        )
    }

    fun recordFailure(
        executionId: String,
        attempt: Int,
        tools: ToolExecutionScope,
    ): Mono<Void> =
        if (claimedTodo.contract ==
            null
        ) {
            Mono.empty()
        } else {
            val captured = result?.takeIf { it.executionId == executionId }
            if (captured != null) {
                taskExecutionService?.reviewed(
                    captured.copy(reviewVerdict = de.heckenmann.visualagent.task.TaskReviewState.FAILED),
                    "Independent review failed; original worker evidence retained",
                ) ?: Mono.empty()
            } else {
                taskExecutionService?.failed(claimedTodo, agent.id, executionId, attempt, tools.observations()) ?: Mono.empty()
            }
        }

    fun captureAndReview(
        text: String,
        tools: ToolExecutionScope,
        executionId: String,
        attempt: Int,
    ): Mono<WorkerReviewResult> {
        if (claimedTodo.contract ==
            null
        ) {
            return taskPlanner.reviewWorkerResult(claimedTodo.id, taskDescription, text, token, tools.evidence())
        }
        val service = taskExecutionService ?: return Mono.error(WorkerReviewFailedException())
        return service.capture(claimedTodo, agent.id, executionId, attempt, text, tools.observations(), inputVersions).flatMap { captured ->
            result = captured
            taskPlanner.reviewContract(claimedTodo, captured, service, token).flatMap { (review, observed) ->
                val reviewed =
                    observed.copy(
                        reviewFeedback = review.feedback.take(8_192),
                        reviewVerdict =
                            de.heckenmann.visualagent.task.TaskReviewState
                                .valueOf(review.verdict.name),
                    )
                result = reviewed
                service.reviewed(reviewed, review.feedback).thenReturn(review)
            }
        }
    }
}
