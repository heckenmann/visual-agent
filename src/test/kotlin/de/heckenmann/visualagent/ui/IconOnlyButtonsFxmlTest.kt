package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconOnlyButtonsFxmlTest {
    private val maintainedFxmlFiles =
        listOf(
            "agent-card.fxml",
            "application-settings.fxml",
            "canvas-panel.fxml",
            "chat-panel.fxml",
            "main-window.fxml",
            "session-panel.fxml",
            "sub-agents-panel.fxml",
            "todo-panel.fxml",
        )

    @Test
    fun `fxml buttons do not expose visible text labels`() {
        val offenders =
            maintainedFxmlFiles.flatMap { file ->
                val xml = fxml(file)
                Regex("""<Button\b[^>]*\btext\s*=""")
                    .findAll(xml)
                    .map { "$file:${xml.lineNumberAt(it.range.first)}" }
                    .toList()
            }

        assertTrue(
            offenders.isEmpty(),
            "FXML buttons must be icon-only and describe actions via tooltips: ${offenders.joinToString()}",
        )
    }

    @Test
    fun `fxml icons use css owned sizes`() {
        val offenders =
            maintainedFxmlFiles.flatMap { file ->
                val xml = fxml(file)
                Regex("""\biconSize\s*=""")
                    .findAll(xml)
                    .map { "$file:${xml.lineNumberAt(it.range.first)}" }
                    .toList()
            }

        assertTrue(
            offenders.isEmpty(),
            "FXML icon sizes must be centralized in application.css for consistent scaling: ${offenders.joinToString()}",
        )
    }

    private fun fxml(file: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fxml/$file")) { "Missing FXML file $file" }
            .bufferedReader()
            .use { it.readText() }

    private fun String.lineNumberAt(index: Int): Int = take(index).count { it == '\n' } + 1
}
