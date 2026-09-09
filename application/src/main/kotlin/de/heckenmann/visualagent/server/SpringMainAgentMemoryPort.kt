package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemory
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryEdit
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.MainAgentMemoryUpdate
import org.springframework.stereotype.Component

/** Exposes the revision-safe main-agent memory document over the application protocol. */
@Component
class SpringMainAgentMemoryPort(
    private val store: MainAgentLongTermMemoryStore,
    private val config: AppConfigBean,
) : MainAgentMemoryPort {
    override fun snapshot(): MainAgentMemorySnapshot = store.snapshot().toProtocol(config.maxMainAgentMemoryChars)

    override fun replace(
        content: String,
        expectedRevision: Long,
    ): MainAgentMemoryUpdate =
        when (val result = store.replace(content, expectedRevision, config.maxMainAgentMemoryChars)) {
            is MainAgentLongTermMemoryEdit.Saved -> MainAgentMemoryUpdate.Saved(result.memory.toProtocol(config.maxMainAgentMemoryChars))
            is MainAgentLongTermMemoryEdit.Conflict ->
                MainAgentMemoryUpdate.Conflict(
                    result.memory.toProtocol(config.maxMainAgentMemoryChars),
                )
        }
}

private fun MainAgentLongTermMemory.toProtocol(limit: Int): MainAgentMemorySnapshot =
    MainAgentMemorySnapshot(content, contentLength, limit, revision)
