package de.heckenmann.visualagent.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies persistence and startup restoration of the optional UI scale preference. */
class AppConfigPersistenceBinderTest {
    @Test
    fun `manual UI scale persists and restores from preferences`() {
        val preferences = NoOpPreferenceStore()
        val saved = AppConfigBean(preferences).apply { uiScalePercent = 125 }

        saved.save()
        val restored = AppConfigBean()
        AppConfigPersistenceBinder(preferences, restored, "test.db").bind()

        assertEquals("125", preferences.getPreference(AppConfigBean.KEY_UI_SCALE_PERCENT))
        assertEquals(125, restored.uiScalePercent)
    }

    @Test
    fun `automatic and invalid UI scale preferences restore as automatic`() {
        val preferences = NoOpPreferenceStore()
        val saved = AppConfigBean(preferences)

        saved.save()
        val automatic = AppConfigBean()
        AppConfigPersistenceBinder(preferences, automatic, "test.db").bind()

        assertEquals("auto", preferences.getPreference(AppConfigBean.KEY_UI_SCALE_PERCENT))
        assertNull(automatic.uiScalePercent)

        preferences.setPreference(AppConfigBean.KEY_UI_SCALE_PERCENT, "201")
        val invalid = AppConfigBean()
        AppConfigPersistenceBinder(preferences, invalid, "test.db").bind()

        assertNull(invalid.uiScalePercent)
    }
}
