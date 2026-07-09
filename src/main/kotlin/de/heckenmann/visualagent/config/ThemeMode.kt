package de.heckenmann.visualagent.config

/**
 * Supported UI theme modes for the Compose desktop application.
 *
 * The mode determines whether the UI renders in light or dark colors. [SYSTEM]
 * attempts to follow the operating-system appearance and falls back to dark
 * when detection is unavailable or fails.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,

    ;

    companion object {
        /**
         * Parses a stored theme-mode preference value.
         *
         * @param value Raw preference string, may be blank
         * @return Parsed mode or [SYSTEM] when unknown or blank
         */
        fun fromString(value: String?): ThemeMode =
            when (value?.trim()?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                "SYSTEM" -> SYSTEM
                else -> SYSTEM
            }
    }
}
