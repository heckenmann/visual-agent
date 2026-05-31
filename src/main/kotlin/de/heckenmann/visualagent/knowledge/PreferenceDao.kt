package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Persists user preference key-value rows.
 *
 * @property connectionProvider Provider for the active SQLite connection
 */
@Component
class PreferenceDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Loads one preference.
     *
     * @param key Preference key
     * @return Stored value or null
     */
    fun getPreference(key: String): String? {
        connectionProvider.get().prepareStatement("SELECT value FROM user_preferences WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getString("value") else null
            }
        }
    }

    /**
     * Saves or replaces one preference.
     *
     * @param key Preference key
     * @param value Preference value
     */
    fun setPreference(
        key: String,
        value: String,
    ) {
        connectionProvider
            .get()
            .prepareStatement(
                """
                INSERT OR REPLACE INTO user_preferences (key, value, updated_at)
                VALUES (?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.setString(3, Instant.now().toString())
                stmt.executeUpdate()
            }
    }
}
