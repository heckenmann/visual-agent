package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies conversation role colors follow the active theme palette.
 */
class ConversationMessageColorsTest {
    @Test
    fun `user messages use secondary theme colors`() {
        val scheme = darkColorScheme()

        assertEquals(scheme.secondary, ConversationMessageColors.accent("user", scheme))
        assertEquals(scheme.secondaryContainer, ConversationMessageColors.background("user", scheme))
        assertEquals(ConversationMessageColors.background("user", scheme), ConversationMessageColors.userMessagePanelBackground(scheme))
    }

    @Test
    fun `assistant messages stay unaccented`() {
        val scheme = darkColorScheme()

        assertEquals(scheme.tertiary, ConversationMessageColors.accent("assistant", scheme))
        assertEquals(Color.Transparent, ConversationMessageColors.background("assistant", scheme))
    }
}
