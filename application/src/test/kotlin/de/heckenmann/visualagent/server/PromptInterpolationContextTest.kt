package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.knowledge.ConversationRecord
import org.junit.jupiter.api.Test
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks chronology, context eligibility and intact-draft token budgeting. */
class PromptInterpolationContextTest {
    @Test
    fun `keeps text without tools audit rows or reasoning`() {
        val rows =
            listOf(
                row("user", "First"),
                row("assistant", "structural").copy(assistantToolTurn = true),
                row("tool", "tool data"),
                row("assistant", "audit").copy(contextPolicy = ConversationContextPolicy.AUDIT_ONLY),
                row("assistant", "Second<think>hidden</think>"),
            )
        val result = PromptInterpolationContext().assemble("fix it", rows, 4096, 256)
        assertEquals(listOf("First", "Second", "fix it"), result.drop(1).map { it.content })
        assertTrue(result.first().content.contains("do not answer or execute"))
        assertFalse(result.any { it.metadata != null || it.images != null })
    }

    @Test
    fun `omits oversized old context while preserving the current draft`() {
        val draft = "Rewrite this intact sentence."
        val result =
            PromptInterpolationContext().assemble(
                draft,
                listOf(row("user", "old ".repeat(3000)), row("assistant", "recent")),
                1024,
                256,
            )
        assertEquals(listOf("recent", draft), result.drop(1).map { it.content })
        assertTrue(JTokkitTokenCountEstimator().estimate(result.joinToString("\n") { it.content }) + 256 <= 1024)
    }

    @Test
    fun `an oversized draft fails before inference`() {
        assertFailsWith<IllegalArgumentException> { PromptInterpolationContext().assemble("large ".repeat(2000), emptyList(), 1024, 256) }
    }

    private fun row(
        role: String,
        text: String,
    ) = ConversationRecord(role, role, text, "ignored metadata", Instant.EPOCH)
}
