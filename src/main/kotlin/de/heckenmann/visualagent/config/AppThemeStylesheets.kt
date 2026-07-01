package de.heckenmann.visualagent.config

/**
 * Resolves configured theme names to stable theme identifiers used by the Compose UI.
 */
object AppThemeStylesheets {
    /**
     * Returns a normalized Compose theme identifier for a configured theme name.
     *
     * @param theme Theme display name stored in app configuration
     * @return Stable theme identifier
     */
    fun stylesheetFor(theme: String): String =
        when (theme) {
            "Primer Dark" -> "primer-dark"
            "Primer Light" -> "primer-light"
            "Nord Dark" -> "nord-dark"
            "Nord Light" -> "nord-light"
            "Cupertino Dark" -> "cupertino-dark"
            "Cupertino Light" -> "cupertino-light"
            else -> "dracula"
        }
}
