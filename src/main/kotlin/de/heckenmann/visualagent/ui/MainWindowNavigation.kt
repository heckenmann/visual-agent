package de.heckenmann.visualagent.ui

import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ChoiceDialog
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination

/**
 * Owns shell navigation shortcuts and command-palette panel switching.
 *
 * @property panelByButton Mapping from navigation button to panel node
 * @property switchPanel Callback that activates a panel and navigation button
 */
internal class MainWindowNavigation(
    private val panelByButton: Map<Button, Node>,
    private val switchPanel: (Node, Button?) -> Unit,
) {
    /**
     * Registers keyboard shortcuts on the active scene.
     *
     * @param targetScene Scene receiving shortcut accelerators
     */
    fun setupKeyboardShortcuts(targetScene: Scene) {
        val buttons = panelByButton.keys.toList()
        val shortcuts =
            mapOf(
                KeyCode.DIGIT1 to buttons.getOrNull(0),
                KeyCode.DIGIT2 to buttons.getOrNull(1),
                KeyCode.DIGIT3 to buttons.getOrNull(2),
                KeyCode.DIGIT4 to buttons.getOrNull(3),
                KeyCode.DIGIT5 to buttons.getOrNull(4),
                KeyCode.DIGIT6 to buttons.getOrNull(5),
            )

        shortcuts.forEach { (keyCode, button) ->
            if (button != null) {
                targetScene.accelerators[KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN)] =
                    Runnable {
                        panelByButton[button]?.let { panel -> switchPanel(panel, button) }
                    }
            }
        }

        targetScene.accelerators[KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN)] =
            Runnable { openCommandPalette() }
    }

    private fun openCommandPalette() {
        val options = listOf("Conversation", "Session", "Agents", "Todos", "Canvas", "Settings")
        val dialog = ChoiceDialog(options.first(), options)
        dialog.title = "Command Palette"
        dialog.headerText = null
        dialog.contentText = "Switch panel:"
        val selected = dialog.showAndWait()
        if (selected.isPresent) {
            val index = options.indexOf(selected.get())
            val button = panelByButton.keys.toList().getOrNull(index) ?: return
            val panel = panelByButton[button] ?: return
            switchPanel(panel, button)
        }
    }
}
