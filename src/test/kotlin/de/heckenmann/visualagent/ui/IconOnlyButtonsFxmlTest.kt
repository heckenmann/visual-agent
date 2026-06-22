package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconOnlyButtonsFxmlTest {
    @Test
    fun `fxml buttons do not expose visible text labels`() {
        val files =
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

        val offenders =
            files.flatMap { file ->
                val xml =
                    requireNotNull(javaClass.getResourceAsStream("/fxml/$file")) { "Missing FXML file $file" }
                        .bufferedReader()
                        .use { it.readText() }
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

    private fun String.lineNumberAt(index: Int): Int = take(index).count { it == '\n' } + 1
}
