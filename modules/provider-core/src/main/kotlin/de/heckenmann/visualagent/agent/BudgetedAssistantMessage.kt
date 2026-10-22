package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.TextPart

/** Replaces text without appending it to the original Spring AI 2.1 message parts. */
fun AssistantMessage.withBudgetedText(text: String): AssistantMessage {
    var replaced = false
    val rewritten =
        parts.mapNotNull { part ->
            if (part !is TextPart) {
                part
            } else if (!replaced) {
                replaced = true
                TextPart.of(text)
            } else {
                null
            }
        }
    return AssistantMessage
        .builder()
        .parts(if (replaced) rewritten else listOf(TextPart.of(text)) + rewritten)
        .properties(metadata)
        .build()
}
