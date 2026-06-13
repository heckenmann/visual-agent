package de.heckenmann.visualagent.ui.panels

import javafx.scene.input.KeyCode

/**
 * Determines whether the add-todo shortcut should open the dialog for a key event.
 *
 * @param code Pressed key code
 * @param shortcutDown Whether Cmd/Ctrl is pressed
 * @param focusOwnerIsTextInput Whether the current focus owner is a text input control
 * @return true when the Add Todo dialog should open
 */
internal fun shouldOpenAddTodoDialog(
    code: KeyCode,
    shortcutDown: Boolean,
    focusOwnerIsTextInput: Boolean,
): Boolean = code == KeyCode.N && shortcutDown && !focusOwnerIsTextInput
