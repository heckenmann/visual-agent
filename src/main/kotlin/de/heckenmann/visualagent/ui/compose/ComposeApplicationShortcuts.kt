package de.heckenmann.visualagent.ui.compose

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal fun KeyEvent.workspaceShortcutDigit(): Int? {
    if (type != KeyEventType.KeyDown || (!isMetaPressed && !isCtrlPressed)) return null
    return when (key) {
        Key.One -> 1
        Key.Two -> 2
        Key.Three -> 3
        Key.Four -> 4
        Key.Five -> 5
        Key.Six -> 6
        else -> null
    }
}

internal fun KeyEvent.isCommandPaletteShortcut(): Boolean = type == KeyEventType.KeyDown && (isMetaPressed || isCtrlPressed) && key == Key.K
