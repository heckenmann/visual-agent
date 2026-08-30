package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.modal.ComposeModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.ComposeSettingsModal
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies the conversation title action for global provider and model settings. */
class ComposeConversationProviderSettingsTest {
    @Test
    fun `provider settings action requests reusable global settings modal`() {
        var requested: ComposeModal? = null

        openConversationProviderSettings(
            modalRequester = ComposeModalRequester { modal -> requested = modal },
            settingsPort = mockk<SettingsPort>(),
            providerPort = mockk<ProviderPort>(),
            onSettingsChanged = {},
        )

        assertTrue(requested is ComposeSettingsModal)
    }
}
