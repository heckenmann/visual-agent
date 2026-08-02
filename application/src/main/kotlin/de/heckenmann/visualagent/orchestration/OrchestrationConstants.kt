package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.Message

/**
 * Central, versioned orchestration prompts and heuristic constants.
 *
 * Keeping prompts in one place makes the agent behaviour inspectable and avoids
 * scattering hard-coded strings across planner code. Use cases: UC-0000056, UC-0000057.
 */
internal object OrchestrationConstants {
    /** Fallback subtask prefix characters stripped during decomposition parsing. */
    val SUBTASK_PREFIX_CHARS = charArrayOf('-', '*', '•')

    /** Minimum meaningful subtask length (in characters) accepted by the planner. */
    const val MIN_SUBTASK_LENGTH = 3

    /** Maximum number of subtasks produced when decomposing a complex todo. */
    const val MAX_SUBTASKS = 8

    /** Minimum word count used as a complexity signal for automatic decomposition. */
    const val COMPLEX_WORD_COUNT = 16

    /** Lexical hints that suggest a todo is complex and should be decomposed. */
    val COMPLEXITY_HINTS = listOf("and", "then", "integrate", "architecture", "pipeline", "multi", "parallel")

    /** Lexical hints that suggest a code-related task. */
    val CODE_HINTS = listOf("code", "implement", "fix")

    /** Lexical hints that suggest a research-related task. */
    val RESEARCH_HINTS = listOf("research", "docs", "issue")

    /** Lexical hints that suggest a testing-related dynamic worker. */
    const val TEST_HINT = "test"

    /** Lexical hints that suggest a review-related dynamic worker. */
    const val REVIEW_HINT = "review"

    /** Lexical hints that suggest a documentation-related dynamic worker. */
    const val DOC_HINT = "doc"

    /** Default names and roles used when dynamically creating worker agents. */
    object DynamicAgent {
        const val TESTER_NAME = "Tester"
        const val TESTER_ROLE = "Focused test implementation and validation"
        const val TESTER_TEMPLATE = "tester"

        const val REVIEWER_NAME = "Reviewer"
        const val REVIEWER_ROLE = "Code and architecture review"
        const val REVIEWER_TEMPLATE = "reviewer"

        const val DOCUMENTER_NAME = "Documenter"
        const val DOCUMENTER_ROLE = "Documentation and developer guides"
        const val DOCUMENTER_TEMPLATE = "documenter"

        const val GENERAL_WORKER_ROLE = "General execution worker"
        const val GENERAL_WORKER_TEMPLATE = "coder"
    }

    /** Default names and roles used when creating the decomposition analysis agent. */
    object AnalysisAgent {
        const val NAME = "Analyst"
        const val ROLE = "Task decomposition and structured planning"
        const val TEMPLATE = "researcher"
    }

    /**
     * Builds the analysis prompt that decomposes a complex todo into subtasks.
     *
     * @param description Original todo description shown to the analysis agent
     * @return System and user messages for the analysis agent
     */
    fun decompositionPrompt(description: String): List<Message> =
        listOf(
            Message(
                "system",
                "You are a task decomposition agent. Your sole responsibility is to break a single " +
                    "high-level task into smaller, self-contained work steps.\n\n" +
                    "Context:\n" +
                    "- You receive one task description from a todo list.\n" +
                    "- Your output will be executed by worker agents, one step at a time, in order.\n" +
                    "- Each subtask becomes a separate todo that a worker picks up and completes.\n" +
                    "- A worker can only perform actions: research, implement, write, edit, analyze, test, review, document.\n" +
                    "- A worker cannot \"be\" the final result. They produce it through actions.\n\n" +
                    "Critical distinction — action vs. content:\n" +
                    "- An ACTION tells a worker what to DO " +
                    "(\"Research the topic\", \"Draft the structure\", \"Write the first section\").\n" +
                    "- CONTENT is the thing being produced (a poem line, a code snippet, a paragraph, a data value).\n" +
                    "- NEVER return content as a subtask. If the task is to create something, describe the creation steps.\n\n" +
                    "Rules:\n" +
                    "1. Produce 2-6 subtasks. Fewer is better if the task is simple.\n" +
                    "2. Every subtask must start with or imply an action verb.\n" +
                    "3. Do not include any content that belongs in the final deliverable.\n" +
                    "4. Each subtask must be understandable in isolation.\n" +
                    "5. If the task spans multiple concerns, split by concern.\n" +
                    "6. If the task is already simple and cannot be meaningfully split, return it as a single subtask.\n\n" +
                    "Self-check before output:\n" +
                    "Read each subtask and ask: \"Would a worker know what ACTION to take, " +
                    "or would they just see a piece of the final result?\" " +
                    "If a subtask looks like a fragment of the deliverable, " +
                    "rewrite it as the action that produces that fragment.\n\n" +
                    "Output format:\n" +
                    "- One subtask per line.\n" +
                    "- No numbering, no prefixes, no bullet points.\n" +
                    "- No additional commentary or explanation.",
            ),
            Message(
                "user",
                "Task: $description\nReturn each subtask on its own line without numbering.",
            ),
        )

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
        systemPrompt: String,
    ): List<Message> =
        listOf(
            Message("system", systemPrompt),
            Message(
                "system",
                "You are now reviewing a sub-agent's work result. Decide whether the task was completed successfully.\n\n" +
                    "Evaluation criteria:\n" +
                    "- Does the result actually address the task description?\n" +
                    "- Is the result concrete and actionable, not vague or evasive?\n" +
                    "- A blank result is acceptable only if the work was done entirely through tool calls.\n" +
                    "- A blank result for a task that requires producing output is a failure.\n\n" +
                    "Respond with exactly APPROVED or RETRY as the first word.",
            ),
            Message(
                "user",
                "Task: $taskDescription\n\nResult:\n${workerResult.ifBlank { "(blank — no text returned)" }}",
            ),
        )
}
