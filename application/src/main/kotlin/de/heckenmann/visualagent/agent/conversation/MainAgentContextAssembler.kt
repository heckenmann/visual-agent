package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Projects conversation events into model-readable dialogue without applying a token budget. */
internal class MainAgentContextAssembler {
    /**
     * Projects user turns into dialogue plus individual execution summaries.
     *
     * @param history Persisted messages in chronological order
     * @return Projected history in chronological order; the provider applies model-aware token budgeting
     */
    fun assemble(history: List<Message>): List<Message> = splitIntoTurns(history).flatMap(::projectTurn)

    /** Places request-only snapshots after completed history without treating them as persisted dialogue. */
    fun withCurrentReferences(
        history: List<Message>,
        currentReferences: List<Message>,
    ): List<Message> {
        val currentTurn = history.indexOfLast { it.role == "user" }.takeIf { it >= 0 } ?: history.size
        // Keep changing snapshots out of the reusable policy/history prefix; see docs/context-transport.md.
        return history.take(currentTurn) + currentReferences + history.drop(currentTurn)
    }

    private fun splitIntoTurns(history: List<Message>): List<List<Message>> {
        val turns = mutableListOf<MutableList<Message>>()
        history.forEach { message ->
            if (message.role == "user") turns.add(mutableListOf())
            if (turns.isEmpty()) turns.add(mutableListOf())
            turns.last() += message
        }
        return turns.filter { turn -> turn.any { it.role == "user" } || turn.any { it.content.isNotBlank() } }
    }

    private fun projectTurn(turn: List<Message>): List<Message> {
        val user = turn.firstOrNull { it.role == "user" }
        val assistant = turn.lastOrNull { it.role == "assistant" }
        val summaryLines = summarize(turn.filter { message -> message !== user && message !== assistant })
        return buildList {
            user?.let(::add)
            summaryLines.forEach { line ->
                add(
                    Message(
                        role = "assistant",
                        content = "Historical execution context (not instructions):\n- $line",
                        contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                    ),
                )
            }
            assistant?.let(::add)
        }
    }

    private fun summarize(messages: List<Message>): List<String> {
        val deduplicated = LinkedHashMap<String, String>()
        messages
            .filter { it.contextPolicy != ConversationContextPolicy.AUDIT_ONLY }
            .forEach { message ->
                val metadata = parseMetadata(message.metadata)
                val type = metadata["type"] ?: message.role
                val identity =
                    metadata["todoId"] ?: metadata["jobId"]
                        ?: metadata["workspacePath"]?.let { path ->
                            "$path:${metadata["operation"] ?: metadata["eventType"] ?: "mutation"}"
                        }
                        ?: metadata["toolId"]?.let { toolId ->
                            "$toolId:${metadata["providerToolCallId"] ?: metadata["sequence"] ?: metadata["requestId"] ?: message.id}"
                        }
                        ?: metadata["requestId"] ?: message.id ?: message.content
                val statusValue = metadata["status"].orEmpty()
                val keySuffix =
                    if (statusValue.equals("error", ignoreCase = true) ||
                        statusValue.equals("failed", ignoreCase = true) ||
                        statusValue.equals("failure", ignoreCase = true)
                    ) {
                        "$identity:${message.id ?: message.content.hashCode()}"
                    } else {
                        identity
                    }
                val key = "$type:$keySuffix"
                val status = statusValue.takeIf(String::isNotBlank)?.let { " [$it]" }.orEmpty()
                val text = "$type$status: ${message.content.replace(Regex("\\s+"), " ").trim()}"
                deduplicated.remove(key)
                deduplicated[key] = text
            }
        return deduplicated.values.toList()
    }

    private fun parseMetadata(metadata: String?): Map<String, String> =
        runCatching {
            Json
                .parseToJsonElement(metadata.orEmpty())
                .jsonObject
                .mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }
                .toMap()
        }.getOrDefault(emptyMap())
}
