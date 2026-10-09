package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import de.heckenmann.visualagent.knowledge.MemoryStore
import kotlinx.coroutines.reactor.awaitSingle

/** Adapts Reactor results to the existing coroutine-based test harness. */
internal suspend fun SubAgent.performTodo(
    todoId: String,
    description: String,
    provider: LLMProvider,
    memoryStore: MemoryStore,
    enabledTools: Set<ToolId> = emptySet(),
    token: CancellationToken? = null,
    onChunk: ((String) -> Unit)? = null,
    onStreamReset: (() -> Unit)? = null,
    requestId: String? = null,
    toolScope: ToolExecutionScope = ToolExecutionScope(),
): String =
    performTodoReactive(
        todoId,
        description,
        provider,
        memoryStore,
        enabledTools,
        token,
        onChunk,
        onStreamReset,
        requestId,
        toolScope,
    ).awaitSingle()

/** Awaits reactive chat at the coroutine test boundary. */
internal suspend fun SubAgent.chat(
    messages: List<Message>,
    provider: LLMProvider,
    enabledTools: Set<ToolId> = emptySet(),
    token: CancellationToken? = null,
    requestId: String? = null,
    executionMetadata: Map<String, Any> = emptyMap(),
): ChatResponse = chatReactive(messages, provider, enabledTools, token, requestId, executionMetadata).awaitSingle()

/** Preserves the existing persistence assertions while exercising the reactive store API. */
internal fun reactiveMemoryStore(): MemoryStore {
    val store = io.mockk.mockk<MemoryStore>(relaxed = true)
    io.mockk.every { store.saveStructuredKnowledgeReactive(any(), any(), any()) } answers {
        val subject = firstArg<String>()
        val summary = secondArg<String>()
        val nextSteps = thirdArg<String?>()
        reactor.core.publisher.Mono
            .fromCallable { store.saveStructuredKnowledge(subject, summary, nextSteps) }
    }
    return store
}
