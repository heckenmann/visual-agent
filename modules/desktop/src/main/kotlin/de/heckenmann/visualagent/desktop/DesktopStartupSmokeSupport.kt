package de.heckenmann.visualagent.desktop

/** Holds the opt-in contract used to start the desktop application in release smoke tests. */
internal object DesktopStartupSmokeSupport {
    /** System property that requests the ordinary local-start action during automated startup. */
    const val AUTO_START_PROPERTY = "visualagent.startup.auto-start-local"

    /** Log marker emitted after the server and initial desktop runtime state are ready. */
    const val READY_LOG_MARKER = "VISUAL_AGENT_DESKTOP_READY"

    /** Parses the smoke-only startup property without enabling it for unspecified values. */
    fun autoStartRequested(value: String?): Boolean = value.equals("true", ignoreCase = true)
}
