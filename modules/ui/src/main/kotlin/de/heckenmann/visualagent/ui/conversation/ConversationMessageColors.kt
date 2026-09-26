package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Resolves conversation message colors from the active theme.
 */
internal object ConversationMessageColors {
    /**
     * Returns a restrained role accent without introducing a custom color.
     *
     * @param role Message role
     * @param colorScheme Active Material theme colors
     * @return Role accent from the active theme
     */
    fun accent(
        role: String,
        colorScheme: ColorScheme,
    ): Color = if (role == "user") colorScheme.secondary else colorScheme.tertiary

    /**
     * Returns the background for a message group.
     *
     * @param role Message role
     * @param colorScheme Active Material theme colors
     * @return Theme container color for user messages or transparent for assistant messages
     */
    fun background(
        role: String,
        colorScheme: ColorScheme,
    ): Color = if (role == "user") colorScheme.secondaryContainer else Color.Transparent
}
