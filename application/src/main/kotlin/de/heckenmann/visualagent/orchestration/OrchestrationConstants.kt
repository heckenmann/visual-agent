package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.Message

/**
 * Central, versioned orchestration prompts.
 *
 * Keeping prompts in one place makes the agent behaviour inspectable and avoids
 * scattering hard-coded strings across planner code. Use cases: UC-0000056, UC-0000057.
 */
internal object OrchestrationConstants {
    /**
     * Builds the instruction shown to a worker sub-agent for a given todo.
     *
     * @param todoId Stable todo identifier
     * @param description User-facing todo description
     * @return Rendered worker instruction
     */
    fun workerInstruction(
        todoId: String,
        description: String,
    ): String =
        """
        Task ID: $todoId
        Objective: $description

        This is the next pending todo by list order. Treat it as the highest priority work item.

        Deliverable requirements:
        1. Provide concrete implementation steps and results.
        2. Explain which files or components were analyzed.
        3. If blocked, include attempted steps and next actions.

        Prioritize local project context first. Inspect project documentation, code comments, and relevant source files.
        If blocked after multiple attempts, gather external references from authoritative sources.
        """.trimIndent()

    /**
     * Builds the review prompt sent to the main agent to evaluate a sub-agent's work result.
     *
     * @param taskDescription Original todo description
     * @param workerResult Text returned by the sub-agent (may be blank)
     * @return System and user messages for the review request
     */
    fun reviewPrompt(
        taskDescription: String,
        workerResult: String,
    ): List<Message> =
        listOf(
            Message(
                "system",
                "You are now reviewing a sub-agent's work result. Decide whether the task was completed successfully.\n\n" +
                    "Evaluation criteria:\n" +
                    "- Does the result actually address the task description?\n" +
                    "- Is the result concrete and actionable, not vague or evasive?\n" +
                    "- A blank result is acceptable only when supplied successful tool evidence establishes the deliverable.\n" +
                    "- Scheduling acknowledgments and absent evidence do not establish completed tool work.\n" +
                    "- Consider tool failures and missing results; do not infer artifact contents that were not supplied.\n" +
                    "- A blank result for a task that requires producing output is a failure.\n\n" +
                    "Return only a JSON object matching this schema, without markdown fences or surrounding prose:\n" +
                    WorkerReviewResult.schema().json + "\n" +
                    "Use verdict APPROVED for completed work or RETRY for work that needs another execution. " +
                    "Provide non-blank, concise user-facing feedback in the task's language. " +
                    "This is the only review: your explanation will be published directly after approval. " +
                    "State limitations honestly; do not claim to have inspected artifacts not included in the result.",
            ),
            Message(
                "user",
                "Task: $taskDescription\n\nResult:\n${workerResult.ifBlank { "(blank — no text returned)" }}",
            ),
        )
}
