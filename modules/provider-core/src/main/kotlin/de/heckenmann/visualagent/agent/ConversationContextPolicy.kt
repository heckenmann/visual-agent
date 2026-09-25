package de.heckenmann.visualagent.agent

import kotlinx.serialization.Serializable

/** Controls whether a persisted conversation entry is eligible for model context. */
@Serializable
enum class ConversationContextPolicy {
    /** User requests and visible assistant outcomes that form conversational turns. */
    DIALOGUE,

    /** Completed or failed activity that may be reduced to a compact execution summary. */
    SUMMARY_SOURCE,

    /** UI/audit activity retained in the timeline but excluded from provider context. */
    AUDIT_ONLY,
    ;

    companion object {
        /** Selects a safe default policy for legacy callers that do not provide one. */
        fun forRole(role: String): ConversationContextPolicy =
            when (role.lowercase()) {
                "user", "assistant" -> DIALOGUE
                else -> SUMMARY_SOURCE
            }
    }
}

/** Metadata key used to preserve context classification across Spring AI prompt rounds. */
object ContextPolicyMetadata {
    /** Spring AI message metadata field carrying a [ConversationContextPolicy] enum name. */
    const val KEY = "visualAgentContextPolicy"
}
