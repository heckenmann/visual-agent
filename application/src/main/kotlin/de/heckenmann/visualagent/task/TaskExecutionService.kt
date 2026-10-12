package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.todo.Todo
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Owns immutable task evidence and durable attempts at the autonomous execution boundary. */
@Service
class TaskExecutionService(
    private val verifier: TaskArtifactVerifier,
    private val attempts: TaskAttemptStore,
) {
    /** Parses a worker-only report and records server observations before review. */
    fun capture(
        todo: Todo,
        agentId: String,
        executionId: String,
        attempt: Int,
        text: String,
        evidence: List<TaskToolObservation>,
        inputVersions: Map<String, String?> = emptyMap(),
    ): Mono<TaskAttemptResult> =
        Mono
            .fromCallable {
                val contract = requireNotNull(todo.contract)
                val reported = WorkerHandoff.parse(text, contract)
                val (artifacts, observations) = verifier.observe(contract)
                TaskAttemptResult(
                    todo.id,
                    agentId,
                    executionId,
                    attempt,
                    contract.revision,
                    TaskPrompts.WORKER_VERSION,
                    reported,
                    artifacts,
                    observations.map { observation ->
                        val criterion = contract.criteria.single { it.id == observation.criterionId }
                        if (criterion.method == TaskCheckMethod.SOURCE_QUOTE &&
                            (
                                inputVersions[criterion.sourceInputId] == null ||
                                    verifier.inputVersions(contract)[criterion.sourceInputId] != inputVersions[criterion.sourceInputId]
                            )
                        ) {
                            observation.copy(state = TaskCheckState.NOT_CHECKED, detail = "Original source bytes missing or changed")
                        } else if (criterion.executionTool != null && evidence.none { it.tool == criterion.executionTool && it.success }) {
                            observation.copy(
                                state = TaskCheckState.NOT_CHECKED,
                                detail = "Required verification tool was not executed successfully",
                            )
                        } else {
                            observation
                        }
                    },
                    evidence,
                    contractSnapshot = contract,
                    inputVersions = inputVersions,
                )
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { result -> attempts.save(result).thenReturn(result) }

    /** Records an execution failure that produced no valid worker handoff. */
    fun failed(
        todo: Todo,
        agentId: String,
        executionId: String,
        attempt: Int,
        evidence: List<TaskToolObservation>,
    ): Mono<Void> {
        val contract = requireNotNull(todo.contract)
        return attempts.save(
            TaskAttemptResult(
                todo.id,
                agentId,
                executionId,
                attempt,
                contract.revision,
                TaskPrompts.WORKER_VERSION,
                WorkerHandoff(
                    "Worker execution failed before a valid reviewable handoff",
                    TaskOutcome.BLOCKED,
                    blockers = listOf("Execution failure"),
                ),
                emptyList(),
                contract.criteria.map { TaskObservation(it.id, TaskCheckState.NOT_CHECKED, "No completed execution evidence") },
                evidence,
                contractSnapshot = contract,
            ),
        )
    }

    /** Stores review feedback on an attempt without promoting it to an accepted result. */
    fun reviewed(
        result: TaskAttemptResult,
        feedback: String,
    ): Mono<Void> = attempts.save(result.copy(reviewFeedback = feedback.take(8_192)))

    /** Bounded detail retrieval at synchronous desktop/tool adapter boundaries. */
    fun attempts(
        todoId: String,
        offset: Int,
        limit: Int,
        accepted: TaskAttemptResult?,
    ): List<TaskAttemptResult> =
        attempts
            .list(todoId, offset, limit)
            .map { result ->
                val selected = if (result.executionId == accepted?.executionId) accepted else result
                val current = selected.contractSnapshot?.let { verifier.observe(it).first }.orEmpty()
                selected.copy(
                    currentAccepted = result.executionId == accepted?.executionId,
                    artifacts =
                        selected.artifacts.map { artifact ->
                            val observed = current.firstOrNull { it.id == artifact.id }
                            artifact.copy(
                                currentState =
                                    if (observed?.state == TaskCheckState.PASSED &&
                                        observed.sha256 == artifact.sha256
                                    ) {
                                        TaskCheckState.PASSED
                                    } else {
                                        TaskCheckState.NOT_CHECKED
                                    },
                            )
                        },
                )
            }.collectList()
            .block()
            .orEmpty()

    /** Reads selected actual content on the server I/O scheduler. */
    fun evidence(contract: TaskContract): Mono<TaskReviewEvidence> =
        Mono.fromCallable { verifier.reviewSnapshot(contract) }.subscribeOn(Schedulers.boundedElastic())

    /** A final deterministic guard; unsupported required checks require user intervention. */
    fun inputVersions(contract: TaskContract): Map<String, String?> = verifier.inputVersions(contract)

    /** Current resource boundary shared with direct and nested tool dispatch. */
    fun resourceAllowed(
        contract: TaskContract,
        path: String,
    ): Boolean = verifier.resourceAllowed(contract, path)

    /** Selected inputs must still match the original server snapshot. */
    fun inputsCurrent(
        contract: TaskContract,
        versions: Map<String, String?>,
    ): Boolean {
        val outputs = contract.deliverables.map { it.path }.toSet()
        val sources =
            contract.criteria
                .filter { it.method == TaskCheckMethod.SOURCE_QUOTE }
                .map { it.sourceInputId }
                .toSet()
        val immutable = contract.inputs.filter { it.path != null && (it.path !in outputs || it.id in sources) }
        val current = verifier.inputVersions(contract)
        return immutable.all { versions[it.id] != null && versions[it.id] == current[it.id] }
    }

    /** A final deterministic guard; unsupported required checks require user intervention. */
    fun canAccept(
        todo: Todo,
        result: TaskAttemptResult,
    ): Boolean {
        val contract = requireNotNull(todo.contract)
        return inputsCurrent(contract, result.inputVersions) &&
            result.reported.outcome == TaskOutcome.PRODUCED &&
            result.reported.blockers.isEmpty() &&
            contract.deliverables.all { artifact -> result.reported.artifacts.any { it.id == artifact.id && it.path == artifact.path } } &&
            contract.criteria.filter { it.required && it.method == TaskCheckMethod.SOURCE_QUOTE }.all { criterion ->
                result.reported.sources.any { it.inputId == criterion.sourceInputId && it.claim.isNotBlank() }
            } &&
            contract.criteria.filter { it.required }.all { criterion ->
                result.observations.any {
                    it.criterionId == criterion.id &&
                        it.state == TaskCheckState.PASSED
                }
            } &&
            result.artifacts.all { it.state == TaskCheckState.PASSED } &&
            verifier.unchanged(contract, result.artifacts)
    }
}
