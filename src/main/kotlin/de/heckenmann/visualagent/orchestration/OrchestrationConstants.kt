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
                "You are an analysis agent. Break down complex tasks into 2-6 concise actionable subtasks.",
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
}
