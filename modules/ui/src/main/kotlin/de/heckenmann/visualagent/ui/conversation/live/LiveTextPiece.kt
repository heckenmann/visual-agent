package de.heckenmann.visualagent.ui.conversation.live

/** One unchanged span or replacement slot in a paragraph; numeric tokens are individual digits. */
internal data class LiveTextPiece(
    val previous: String,
    val current: String,
) {
    /** Whether this slot contains a replacement rather than unchanged surrounding text. */
    val changed: Boolean get() = previous != current
}
