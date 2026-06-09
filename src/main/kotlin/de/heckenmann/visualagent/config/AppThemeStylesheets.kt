package de.heckenmann.visualagent.config

/**
 * Resolves configured theme names to AtlantaFX user-agent stylesheets.
 */
object AppThemeStylesheets {
    /**
     * Returns the AtlantaFX stylesheet for a configured theme name.
     *
     * @param theme Theme display name stored in app configuration
     * @return User-agent stylesheet URL
     */
    fun stylesheetFor(theme: String): String =
        when (theme) {
            "Primer Dark" ->
                atlantafx.base.theme
                    .PrimerDark()
                    .getUserAgentStylesheet()
            "Primer Light" ->
                atlantafx.base.theme
                    .PrimerLight()
                    .getUserAgentStylesheet()
            "Nord Dark" ->
                atlantafx.base.theme
                    .NordDark()
                    .getUserAgentStylesheet()
            "Nord Light" ->
                atlantafx.base.theme
                    .NordLight()
                    .getUserAgentStylesheet()
            "Cupertino Dark" ->
                atlantafx.base.theme
                    .CupertinoDark()
                    .getUserAgentStylesheet()
            "Cupertino Light" ->
                atlantafx.base.theme
                    .CupertinoLight()
                    .getUserAgentStylesheet()
            else ->
                atlantafx.base.theme
                    .Dracula()
                    .getUserAgentStylesheet()
        }
}
