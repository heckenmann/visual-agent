package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Persists sub-agent rows.
 *
 * @property connectionProvider Provider for the active SQLite connection
 */
@Component
class SubAgentDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Saves or updates a sub-agent.
     *
     * @return true for insert, false for update
     */
    fun saveAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String? = null,
        parentAgentId: String? = null,
        configJson: String,
    ): Boolean =
        if (agentExists(id)) {
            updateAgent(id, name, role, status, currentTask, parentAgentId, configJson)
            false
        } else {
            insertAgent(id, name, role, status, currentTask, parentAgentId, configJson)
            true
        }

    /**
     * Loads one agent by ID.
     *
     * @param id Agent ID
     * @return Row map or null
     */
    fun getAgent(id: String): Map<String, Any>? {
        connectionProvider.get().prepareStatement("SELECT * FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.toAgentRow() else null
            }
        }
    }

    /**
     * Lists agents, optionally filtered by status.
     *
     * @param status Optional status filter
     * @return Agent rows
     */
    fun listAgents(status: String? = null): List<Map<String, Any>> {
        val query =
            if (status != null) {
                "SELECT * FROM sub_agents WHERE status = ? ORDER BY created_at DESC"
            } else {
                "SELECT * FROM sub_agents ORDER BY created_at DESC"
            }
        val agents = mutableListOf<Map<String, Any>>()
        connectionProvider.get().prepareStatement(query).use { stmt ->
            if (status != null) stmt.setString(1, status)
            stmt.executeQuery().use { rs ->
                while (rs.next()) agents += rs.toAgentRow()
            }
        }
        return agents
    }

    /**
     * Deletes one sub-agent.
     *
     * @param id Agent ID
     * @return true when a row was deleted
     */
    fun deleteAgent(id: String): Boolean {
        connectionProvider.get().prepareStatement("DELETE FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Updates agent status and current task.
     *
     * @return true when a row was updated
     */
    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean {
        connectionProvider.get().prepareStatement(UPDATE_STATUS_SQL).use { stmt ->
            stmt.setString(1, status)
            stmt.setString(2, currentTask)
            stmt.setString(3, Instant.now().toString())
            stmt.setString(4, id)
            return stmt.executeUpdate() > 0
        }
    }

    private fun agentExists(id: String): Boolean =
        connectionProvider.get().prepareStatement("SELECT 1 FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { it.next() }
        }

    private fun insertAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String?,
        parentAgentId: String?,
        configJson: String,
    ) {
        connectionProvider.get().prepareStatement(INSERT_SQL).use { stmt ->
            stmt.setString(1, id)
            bindCommon(stmt, 2, name, role, status, currentTask, parentAgentId, configJson)
            stmt.setString(8, Instant.now().toString())
            stmt.setString(9, Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    private fun updateAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String?,
        parentAgentId: String?,
        configJson: String,
    ) {
        connectionProvider.get().prepareStatement(UPDATE_SQL).use { stmt ->
            bindCommon(stmt, 1, name, role, status, currentTask, parentAgentId, configJson)
            stmt.setString(7, Instant.now().toString())
            stmt.setString(8, id)
            stmt.executeUpdate()
        }
    }

    private fun bindCommon(
        stmt: java.sql.PreparedStatement,
        firstIndex: Int,
        name: String,
        role: String,
        status: String,
        currentTask: String?,
        parentAgentId: String?,
        configJson: String,
    ) {
        stmt.setString(firstIndex, name)
        stmt.setString(firstIndex + 1, role)
        stmt.setString(firstIndex + 2, status)
        stmt.setString(firstIndex + 3, currentTask)
        stmt.setString(firstIndex + 4, parentAgentId)
        stmt.setString(firstIndex + 5, configJson)
    }

    private fun java.sql.ResultSet.toAgentRow(): Map<String, Any> =
        mapOf(
            "id" to getString("id"),
            "name" to getString("name"),
            "role" to getString("role"),
            "status" to getString("status"),
            "currentTask" to (getString("current_task") ?: ""),
            "parentAgentId" to (getString("parent_agent_id") ?: ""),
            "config" to getString("config"),
            "createdAt" to getString("created_at"),
            "updatedAt" to getString("updated_at"),
        )

    private companion object {
        private const val INSERT_SQL =
            """
            INSERT INTO sub_agents (id, name, role, status, current_task, parent_agent_id, config, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        private const val UPDATE_SQL =
            """
            UPDATE sub_agents
            SET name = ?, role = ?, status = ?, current_task = ?, parent_agent_id = ?, config = ?, updated_at = ?
            WHERE id = ?
            """
        private const val UPDATE_STATUS_SQL =
            """
            UPDATE sub_agents
            SET status = ?, current_task = ?, updated_at = ?
            WHERE id = ?
            """
    }
}
