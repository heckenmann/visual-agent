package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the bounded and deterministic main-agent context projection. */
class MainAgentContextAssemblerTest {
    private val assembler = MainAgentContextAssembler()

    @Test
    fun `projects dialogue and summarizes execution events while dropping audit-only records`() {
        val history =
            listOf(
                Message("user", "First request", id = "user-1"),
                Message("assistant", "First answer", id = "assistant-1"),
                Message(
                    "system",
                    "todo progress",
                    metadata = """{"type":"todo","todoId":"todo-1","status":"processing"}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message(
                    "system",
                    "internal trace",
                    contextPolicy = ConversationContextPolicy.AUDIT_ONLY,
                ),
                Message("user", "Second request", id = "user-2"),
                Message("assistant", "Second answer", id = "assistant-2"),
            )

        val result = assembler.assemble(history, "You are the main agent.", 4096)
        val content = result.joinToString("\n") { it.content }

        assertTrue(content.contains("First request"))
        assertTrue(content.contains("Second answer"))
        assertTrue(content.contains("Execution summary:"))
        assertFalse(content.contains("internal trace"))
    }

    @Test
    fun `keeps failure events even when the same execution later succeeds`() {
        val history =
            listOf(
                Message("user", "Run the task"),
                Message(
                    "system",
                    "failed attempt",
                    metadata = """{"type":"todo","todoId":"todo-1","status":"failed"}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message(
                    "system",
                    "successful retry",
                    metadata = """{"type":"todo","todoId":"todo-1","status":"completed"}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message("assistant", "Done"),
            )

        val summary = assembler.assemble(history, "System", 4096).first { it.content.startsWith("Execution summary:") }

        assertTrue(summary.content.contains("failed attempt"))
        assertTrue(summary.content.contains("successful retry"))
    }

    @Test
    fun `keeps distinct tool calls from one request in the summary`() {
        val history =
            listOf(
                Message("user", "Inspect the workspace"),
                Message(
                    "tool",
                    "read result",
                    metadata = """{"type":"tool_call","toolId":"file:read","requestId":"request-1","sequence":1}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message(
                    "tool",
                    "list result",
                    metadata = """{"type":"tool_call","toolId":"file:list","requestId":"request-1","sequence":2}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message("assistant", "Workspace inspected"),
            )

        val summary = assembler.assemble(history, "System", 4096).first { it.content.startsWith("Execution summary:") }

        assertTrue(summary.content.contains("read result"))
        assertTrue(summary.content.contains("list result"))
    }

    @Test
    fun `keeps workspace operations distinct when they target the same path`() {
        val history =
            listOf(
                Message("user", "Update the report"),
                Message(
                    "system",
                    "Report was read",
                    metadata = """{"type":"workspace_file","workspacePath":"reports/report.md","operation":"read"}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message(
                    "system",
                    "Report was written",
                    metadata = """{"type":"workspace_file","workspacePath":"reports/report.md","operation":"write"}""",
                    contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                ),
                Message("assistant", "The report was updated"),
            )

        val summary = assembler.assemble(history, "System", 4096).first { it.content.startsWith("Execution summary:") }

        assertTrue(summary.content.contains("Report was read"))
        assertTrue(summary.content.contains("Report was written"))
    }

    @Test
    fun `reports execution events omitted by the per-turn summary limit`() {
        val history =
            buildList {
                add(Message("user", "Process the batch"))
                repeat(30) { index ->
                    add(
                        Message(
                            "system",
                            "Completed item $index",
                            metadata = """{"type":"workspace_file","workspacePath":"batch/item-$index.txt","operation":"write"}""",
                            contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                        ),
                    )
                }
                add(Message("assistant", "Batch complete"))
            }

        val summary = assembler.assemble(history, "System", 4096).first { it.content.startsWith("Execution summary:") }

        assertTrue(summary.content.contains("Additional execution events omitted: 6."))
    }

    @Test
    fun `retains newest turns when token budget is exhausted`() {
        val history =
            buildList {
                repeat(12) { index ->
                    add(Message("user", "Request $index " + "x".repeat(800)))
                    add(Message("assistant", "Answer $index " + "y".repeat(800)))
                }
            }

        val result =
            assembler.assemble(history, "System", 1024)
        val content = result.joinToString("\n") { it.content }

        assertTrue(content.contains("Request 11"))
        assertFalse(content.contains("Request 0"))
        assertTrue(content.contains("older conversation turn(s) omitted"))
    }

    @Test
    fun `does not retain stale turns after a newer turn exceeds the budget`() {
        val history =
            listOf(
                Message("user", "Old request"),
                Message("assistant", "Old answer"),
                Message("user", "Oversized request " + "x".repeat(8_000)),
                Message("assistant", "Oversized answer " + "y".repeat(8_000)),
                Message("user", "Current request"),
                Message("assistant", "Current answer"),
            )

        val content = assembler.assemble(history, "System", 1_800).joinToString("\n") { it.content }

        assertTrue(content.contains("Current request"))
        assertFalse(content.contains("Old request"))
    }

    @Test
    fun `keeps the latest event for a deduplicated identity within the summary limit`() {
        val history =
            buildList {
                add(Message("user", "Process the workspace"))
                repeat(25) { index ->
                    add(
                        Message(
                            "system",
                            "Initial event $index",
                            metadata = """{"type":"workspace_file","workspacePath":"batch/$index.txt","operation":"write"}""",
                            contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                        ),
                    )
                }
                add(
                    Message(
                        "system",
                        "Final event 0",
                        metadata = """{"type":"workspace_file","workspacePath":"batch/0.txt","operation":"write","status":"completed"}""",
                        contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                    ),
                )
                add(Message("assistant", "Finished"))
            }

        val summary = assembler.assemble(history, "System", 4_096).first { it.content.startsWith("Execution summary:") }

        assertTrue(summary.content.contains("Final event 0"))
        assertFalse(summary.content.contains("Initial event 0"))
    }
}
