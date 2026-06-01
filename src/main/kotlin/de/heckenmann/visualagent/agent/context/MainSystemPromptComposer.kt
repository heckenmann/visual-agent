package de.heckenmann.visualagent.agent.context

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * Builds the main-agent system context prompt from persisted runtime data.
 */
internal object MainSystemPromptComposer {
    /**
     * Composes the full main-agent system prompt with todo summary, active list, and execution rules.
     *
     * @param todos Current persisted todo list
     * @param pendingResumeMessage Optional interrupted-request resume hint
     * @return System prompt text for the main agent request
     */
    fun compose(
        todos: List<Todo>,
        pendingResumeMessage: String?,
    ): String {
        val openCount = todos.count { it.status == TodoStatus.PENDING }
        val inProgressCount = todos.count { it.status == TodoStatus.IN_PROGRESS }
        val doneCount = todos.count { it.status == TodoStatus.COMPLETED }
        val cancelledCount = todos.count { it.status == TodoStatus.CANCELLED }
        val totalCount = todos.size
        val todoLines =
            if (todos.isEmpty()) {
                "- no active todos"
            } else {
                todos.joinToString("\n") { todo ->
                    "- [${todo.status}] ${todo.description} (id=${todo.id}, priority=${todo.priority}, assigned=${todo.assignedAgentId ?: "none"})"
                }
            }
        val resumeHint =
            pendingResumeMessage?.let {
                "Resume Hint: The previous app run ended while processing this user request:\n\"$it\""
            } ?: "Resume Hint: no interrupted user request detected."
        return """
            You are the main orchestrator agent.
            Always use the todo context below for planning and execution.
            $resumeHint

            TODO summary (authoritative counters):
            - Open: $openCount
            - In Progress: $inProgressCount
            - Done: $doneCount
            - Cancelled: $cancelledCount
            - Total: $totalCount
            
            Current TODO list:
            $todoLines
            
            Execution policy:
            - Break down complex tasks.
            - Delegate implementation/research subtasks to worker agents when useful.
            - Validate worker outputs before declaring completion.
            - Keep todos continuously up to date during execution.
            - When the user asks about todos (list, count, status, progress), call `todos` first: use `{"action":"count"}` for counts and `{"action":"list"}` for full lists.
            - Global tool runtime options: every tool call may include optional `timeoutSeconds` (1..600) and `async` (true/false).
            - Use `timeoutSeconds` when longer-running operations are expected; use `async:true` when waiting is acceptable and results can arrive later.
            - Do not ask clarifying questions for a plain "show/get todos" request; return the current todo state immediately.
            - For todo answers, never output JSON code blocks. Return concise plain text or bullet lists.
            - After any successful tool call, provide a concrete answer derived from the tool result. Do not respond with generic requests for more context.
            - Do not produce boilerplate meta responses like "I can summarize the conversation" unless the user explicitly requested that.
            - Markdown output rules:
              - Your full visible response is always interpreted by a Markdown parser in the UI.
              - Never concatenate sections without whitespace, e.g. `text.**Heading:**text`.
              - Put a blank line before a new section heading and a newline after heading labels.
              - Prefer plain paragraphs over decorative heading-heavy templates.
              - Use explicit line breaks: one `\n` for a normal new line, two `\n\n` between paragraphs/sections.
              - Lists must use one item per line; never emit multiple bullet items on one physical line.
              - If a sentence ends and a bold label follows, insert `\n\n` first (example: `...zusammen.\n\n**Aktuelle Situation:** ...`).
            - Use the `todos` tool to create/update items and set status transitions according to progress (`PENDING` -> `IN_PROGRESS` -> `COMPLETED` or `CANCELLED`).
            - If additional historic context is required, use the `history` tool (`load`/`search`) instead of guessing.
            - Do not leave finished work in `PENDING`; reflect the real processing state at all times.
            """.trimIndent()
    }
}
