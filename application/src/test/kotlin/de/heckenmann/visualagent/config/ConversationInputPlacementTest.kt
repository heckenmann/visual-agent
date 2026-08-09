package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationInputPlacementTest {
    @Test
    fun `placement defaults to conversation message and invalid persisted values fall back to it`() {
        assertEquals(ConversationInputPlacement.CONVERSATION_MESSAGE, AppConfigBean().conversationInputPlacement)
        assertEquals(ConversationInputPlacement.CONVERSATION_MESSAGE, ConversationInputPlacement.fromString("unknown"))
    }

    @Test
    fun `placement persists and is restored by the application config binder`() {
        val store = InMemoryPreferenceStore()
        val config = AppConfigBean(store)
        config.conversationInputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE
        config.save()

        val restored = AppConfigBean(store)
        AppConfigPersistenceBinder(
            store,
            restored,
            "./data/visual-agent.db",
        ).bind()

        assertEquals("CONVERSATION_MESSAGE", store.getPreference(AppConfigBean.KEY_UI_CONVERSATION_INPUT_PLACEMENT))
        assertEquals(ConversationInputPlacement.CONVERSATION_MESSAGE, restored.conversationInputPlacement)
    }

    @Test
    fun `panel labels default to expanded and restore an explicit compact preference`() {
        assertEquals(true, AppConfigBean().showPanelLabels)
        val store = InMemoryPreferenceStore()
        val config = AppConfigBean(store)
        config.showPanelLabels = false
        config.save()

        val restored = AppConfigBean(store)
        AppConfigPersistenceBinder(store, restored, "./data/visual-agent.db").bind()

        assertEquals("false", store.getPreference(AppConfigBean.KEY_UI_SHOW_PANEL_LABELS))
        assertEquals(false, restored.showPanelLabels)
    }

    @Test
    fun `saving panel labels writes only its preference`() {
        val store = InMemoryPreferenceStore()
        val config = AppConfigBean(store)
        config.showPanelLabels = false

        config.savePanelLabels()

        assertEquals("false", store.getPreference(AppConfigBean.KEY_UI_SHOW_PANEL_LABELS))
        assertEquals(1, store.values.size)
    }

    private class InMemoryPreferenceStore : PreferenceStore {
        val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
