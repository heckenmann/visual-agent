package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class MainWindowStylingSourceTest {
    @Test
    fun `main window applies font scale through css classes`() {
        val source = Path.of("src/main/kotlin/de/heckenmann/visualagent/ui/MainWindow.kt").readText()

        assertTrue(
            source.contains("UI_FONT_CLASS_PREFIX"),
            "MainWindow must use named font CSS classes for the user-selected font size.",
        )
        assertFalse(
            source.contains("scene?.root?.style ="),
            "MainWindow must not apply the root font size with inline CSS.",
        )
    }
}
