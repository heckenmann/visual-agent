package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals

class PanelLabelPersistenceTest {
    @Test
    fun `flush writes the pending label preference before closing`() {
        val store = InMemoryPreferenceStore()
        val config = AppConfigBean(store).also { it.showPanelLabels = false }
        val persistence = PanelLabelPersistence(config)

        persistence.persist()
        persistence.flushAndClose()

        assertEquals("false", store.getPreference(AppConfigBean.KEY_UI_SHOW_PANEL_LABELS))
    }

    @Test
    fun `closed persistence ignores subsequent writes`() {
        val store = InMemoryPreferenceStore()
        val config = AppConfigBean(store).also { it.showPanelLabels = false }
        val persistence = PanelLabelPersistence(config)

        persistence.persist()
        persistence.flushAndClose()
        config.showPanelLabels = true
        persistence.persist()

        assertEquals("false", store.getPreference(AppConfigBean.KEY_UI_SHOW_PANEL_LABELS))
    }

    private class InMemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
