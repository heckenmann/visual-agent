package de.heckenmann.visualagent.ui.panels.chat

import de.heckenmann.visualagent.AppIdentity
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Binds ChatPanel FXML controls to callbacks and one-time UI state.
 */
internal class ChatPanelInitializer(
    private val owner: Region,
    private val rootBorderPane: BorderPane,
    private val messagesScrollPane: ScrollPane,
    private val inputTextField: TextArea,
    private val sendButton: Button,
    private val clearChatButton: Button,
    private val todoInfoLabel: Label,
    private val openTodosButton: Button,
    private val openFileButton: Button,
    private val assistantBusyContainer: HBox,
    private val conversationIconImage: ImageView,
    private val todoSummaryTooltip: Tooltip,
    private val messageList: ChatMessageListController,
) {
    /**
     * Applies static state and event handlers.
     */
    fun initialize(
        sendMessage: () -> Unit,
        clearConversation: () -> Unit,
        openTodos: () -> Unit,
        openFile: () -> Unit,
        loadOlderMessages: () -> Unit,
    ) {
        AppIdentity.javaFxIcon()?.let { conversationIconImage.image = it }
        sendButton.graphic = FontIcon(FontAwesomeSolid.PAPER_PLANE)
        sendButton.tooltip = Tooltip("Send")
        clearChatButton.graphic = FontIcon(FontAwesomeSolid.BROOM)
        clearChatButton.tooltip = Tooltip("Clear conversation")
        clearChatButton.isFocusTraversable = false
        openTodosButton.tooltip = Tooltip("Open todo list")
        openTodosButton.isFocusTraversable = false
        openFileButton.graphic = FontIcon(FontAwesomeSolid.FOLDER_OPEN)
        openFileButton.tooltip = Tooltip("Import files into workspace")
        openFileButton.isFocusTraversable = false
        Tooltip.install(todoInfoLabel, todoSummaryTooltip)
        messagesScrollPane.hvalue = 0.0
        bindScroll(loadOlderMessages)
        sendButton.setOnAction { sendMessage() }
        openTodosButton.setOnAction { openTodos() }
        openFileButton.setOnAction { openFile() }
        clearChatButton.setOnAction { clearConversation() }
        assistantBusyContainer.isManaged = false
        assistantBusyContainer.isVisible = false
        bindInputShortcut(sendMessage)
        bindSceneFocusShortcut()
        bindSizing()
    }

    private fun bindScroll(loadOlderMessages: () -> Unit) {
        messagesScrollPane.vvalueProperty().addListener { _, _, newValue ->
            if (messageList.loadingOlderMessages) return@addListener
            if (newValue.toDouble() <= 0.01) {
                messageList.loadingOlderMessages = true
                loadOlderMessages()
            }
        }
    }

    private fun bindInputShortcut(sendMessage: () -> Unit) {
        inputTextField.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            when (keyboardAction(event.code, event.isShiftDown, event.isShortcutDown)) {
                InputKeyboardAction.INSERT_LINE_BREAK -> {
                    inputTextField.replaceSelection("\n")
                    event.consume()
                }
                InputKeyboardAction.SEND -> {
                    sendMessage()
                    event.consume()
                }
                InputKeyboardAction.NONE -> Unit
            }
        }
    }

    private fun bindSceneFocusShortcut() {
        rootBorderPane.sceneProperty().addListener { _, _, newScene ->
            newScene?.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
                if (ev.text == "/" && ev.code == KeyCode.SLASH) {
                    inputTextField.requestFocus()
                    ev.consume()
                }
            }
        }
    }

    private fun bindSizing() {
        VBox.setVgrow(owner, Priority.ALWAYS)
        BorderPane.setAlignment(rootBorderPane, javafx.geometry.Pos.CENTER)
        owner.widthProperty().addListener { _, _, newW ->
            rootBorderPane.prefWidth = newW.toDouble() - 1
        }
        owner.heightProperty().addListener { _, _, newH ->
            rootBorderPane.prefHeight = newH.toDouble() - 1
        }
    }

    internal companion object {
        /**
         * Maps chat input key state to the composer action.
         */
        fun keyboardAction(
            code: KeyCode,
            shiftDown: Boolean,
            shortcutDown: Boolean,
        ): InputKeyboardAction =
            when {
                code != KeyCode.ENTER -> InputKeyboardAction.NONE
                shiftDown -> InputKeyboardAction.INSERT_LINE_BREAK
                shortcutDown -> InputKeyboardAction.SEND
                else -> InputKeyboardAction.SEND
            }
    }
}

internal enum class InputKeyboardAction {
    NONE,
    SEND,
    INSERT_LINE_BREAK,
}
