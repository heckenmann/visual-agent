package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.SubAgentToolConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Persists sub-agent tool configuration rows.
 *
 * @property connectionProvider Provider for the active SQLite connection
 */
@Component
class SubAgentConfigDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Saves or replaces one tool configuration.
     */
    fun saveSubAgentConfig(
        id: String,
        name: String,
        description: String,
        model: String,
        systemPrompt: String,
        toolsJson: String,
        maxTurns: Int,
        enabled: Boolean,
    ) {
        connectionProvider.get().prepareStatement(UPSERT_SQL).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, name)
            stmt.setString(3, description)
            stmt.setString(4, model)
            stmt.setString(5, systemPrompt)
            stmt.setString(6, toolsJson)
            stmt.setInt(7, maxTurns)
            stmt.setInt(8, if (enabled) 1 else 0)
            stmt.setString(9, id)
            stmt.setString(10, Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    /**
     * Loads one persisted tool configuration.
     *
     * @param id Configuration ID
     * @return Configuration or null
     */
    fun getSubAgentConfig(id: String): SubAgentToolConfig? {
        connectionProvider.get().prepareStatement("SELECT * FROM sub_agent_configs WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return SubAgentToolConfig(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    model = rs.getString("model"),
                    systemPrompt = rs.getString("system_prompt"),
                    tools = runCatching { Json.decodeFromString<List<String>>(rs.getString("tools")) }.getOrElse { emptyList() },
                    maxTurns = rs.getInt("max_turns"),
                    enabled = rs.getInt("enabled") == 1,
                )
            }
        }
    }

    /**
     * Lists all persisted tool configurations.
     *
     * @return Configurations ordered by ID
     */
    fun listSubAgentConfigs(): List<SubAgentToolConfig> {
        val configs = mutableListOf<SubAgentToolConfig>()
        connectionProvider.get().prepareStatement("SELECT id FROM sub_agent_configs ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    getSubAgentConfig(rs.getString("id"))?.let(configs::add)
                }
            }
        }
        return configs
    }

    private companion object {
        private val UPSERT_SQL =
            """
            INSERT OR REPLACE INTO sub_agent_configs
                (id, name, description, model, system_prompt, tools, max_turns, enabled, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM sub_agent_configs WHERE id = ?), ?))
            """.trimIndent()
    }
}
