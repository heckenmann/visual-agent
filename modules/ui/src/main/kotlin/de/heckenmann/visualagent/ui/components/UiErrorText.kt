package de.heckenmann.visualagent.ui.components

/** Converts an operation failure into a bounded presentation-safe status message. */
internal fun Throwable.toUiErrorMessage(): String {
    val detail = message?.trim()?.replace(Regex("\\s+"), " ")?.take(180)
    return if (detail.isNullOrBlank()) "Operation failed" else "Operation failed: $detail"
}
