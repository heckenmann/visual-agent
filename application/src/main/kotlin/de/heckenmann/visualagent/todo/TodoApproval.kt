package de.heckenmann.visualagent.todo

/**
 * Trusted main-model feedback from a successfully reviewed worker request.
 *
 * @property feedback User-facing result of the completed main-model review
 * @property conversationRequestId Original worker request invalidated by conversation reset
 */
data class TodoApproval(
    val feedback: String,
    val conversationRequestId: String,
)
