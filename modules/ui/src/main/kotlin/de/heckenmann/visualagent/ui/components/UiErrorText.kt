package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.protocol.ProtocolOperationException

/** Converts a protocol failure into a presentation-safe status message. */
internal fun Throwable.toUiErrorMessage(): String =
    when (this) {
        is ProtocolOperationException -> "${error.summary}: ${error.detail}"
        else -> "Operation failed"
    }
