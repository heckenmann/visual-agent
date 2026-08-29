package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Persists reusable sub-agent tool configurations. */
@Service
internal class JpaSubAgentConfigStore(
    private val repository: SubAgentConfigRepository,
) : SubAgentConfigStore {
    @Transactional
    override fun saveSubAgentConfig(config: SubAgentToolConfig) {
        val createdAt = repository.findById(config.id).orElse(null)?.createdAt ?: Instant.now()
        repository.save(
            SubAgentConfigEntity(
                id = config.id,
                name = config.name,
                description = config.description,
                model = config.model,
                systemPrompt = config.systemPrompt,
                tools = Json.encodeToString(config.tools),
                maxTurns = config.maxTurns,
                enabled = config.enabled,
                createdAt = createdAt,
            ),
        )
    }

    @Transactional(readOnly = true)
    override fun getSubAgentConfig(id: String): SubAgentToolConfig? = repository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun listSubAgentConfigs(): List<SubAgentToolConfig> = repository.findAllByOrderByIdAsc().map(SubAgentConfigEntity::toDomain)
}

private fun SubAgentConfigEntity.toDomain(): SubAgentToolConfig =
    SubAgentToolConfig(
        id = id,
        name = name,
        description = description,
        model = model,
        systemPrompt = systemPrompt,
        tools = runCatching { Json.decodeFromString<List<String>>(tools) }.getOrElse { emptyList() },
        maxTurns = maxTurns,
        enabled = enabled,
    )
