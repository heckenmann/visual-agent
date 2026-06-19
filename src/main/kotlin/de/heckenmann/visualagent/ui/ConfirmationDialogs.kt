package de.heckenmann.visualagent.ui

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType

/**
 * Shared confirmation dialogs for destructive UI actions.
 */
internal object ConfirmationDialogs {
    fun confirm(
        title: String,
        header: String,
        message: String,
    ): Boolean =
        Alert(
            Alert.AlertType.CONFIRMATION,
            message,
            ButtonType.CANCEL,
            ButtonType.OK,
        ).apply {
            this.title = title
            headerText = header
        }.showAndWait()
            .orElse(ButtonType.CANCEL) == ButtonType.OK
}
