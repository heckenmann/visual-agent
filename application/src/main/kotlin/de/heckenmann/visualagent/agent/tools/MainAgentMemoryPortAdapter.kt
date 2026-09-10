package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryEditResult
import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryPort
import de.heckenmann.visualagent.agent.tools.api.MainAgentMemorySnapshot
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryEdit
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import org.springframework.stereotype.Component

/** Adapts durable main-agent memory persistence to the provider-neutral tool boundary. */
@Component
class MainAgentMemoryPortAdapter(
    private val store: MainAgentLongTermMemoryStore,
    private val config: AppConfigBean,
) : MainAgentMemoryPort {
    override fun show(): MainAgentMemorySnapshot = store.snapshot().toToolSnapshot(config.maxMainAgentMemoryChars)

    override fun edit(
        content: String,
        expectedRevision: Long,
    ): MainAgentMemoryEditResult =
        when (val result = store.replace(content, expectedRevision, config.maxMainAgentMemoryChars)) {
            is MainAgentLongTermMemoryEdit.Saved ->
                MainAgentMemoryEditResult.Saved(
                    result.memory.toToolSnapshot(config.maxMainAgentMemoryChars),
                )
            is MainAgentLongTermMemoryEdit.Conflict ->
                MainAgentMemoryEditResult.Conflict(
                    result.memory.toToolSnapshot(config.maxMainAgentMemoryChars),
                )
        }
}

private fun de.heckenmann.visualagent.knowledge.MainAgentLongTermMemory.toToolSnapshot(limit: Int): MainAgentMemorySnapshot =
    MainAgentMemorySnapshot(content, revision, contentLength, limit)
