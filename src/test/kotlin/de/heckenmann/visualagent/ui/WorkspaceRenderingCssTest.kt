package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceRenderingCssTest {
    @Test
    fun `workspace chrome avoids expensive css effects`() {
        val css = applicationCss()

        assertFalse(
            css.contains("-fx-effect"),
            "Workspace stylesheet must avoid JavaFX effects because they make large internal windows lag during drag and scroll.",
        )
    }

    @Test
    fun `workspace windows have cheap visible separation cues`() {
        val css = applicationCss()

        assertTrue(
            css.contains("-fx-border-width: 2px;"),
            "Workspace windows need a consistent border width to avoid hard visual joins.",
        )
        assertFalse(
            css.contains("-fx-border-width: 2px 2px 2px 5px;"),
            "Workspace windows must not use asymmetric border widths because the corner transition looks abrupt.",
        )
        assertFalse(
            css.contains(".workspace-window-active {\n    -fx-border-color: -color-accent-emphasis;\n    -fx-border-width:"),
            "Active workspace windows must not change border width because JavaFX cannot animate that transition smoothly.",
        )
        assertTrue(
            css.contains("linear-gradient(from 0% 0% to 100% 0%, -color-accent-emphasis 0%, -color-accent-muted 16%,"),
            "Active workspace windows need a smooth header accent instead of a thick side border.",
        )
        assertTrue(
            css.contains(".workspace-window-active .workspace-window-header"),
            "Active workspace windows need a distinct header state.",
        )
        assertTrue(
            css.contains(".workspace-window-active .workspace-window-title"),
            "Active workspace windows need title contrast for visual focus.",
        )
    }

    @Test
    fun `canvas toolbar uses theme controlled icon button metrics`() {
        val css = applicationCss()

        assertTrue(
            css.contains(".canvas-toolbar {\n    -fx-background-color: -color-bg-default;"),
            "Canvas toolbar styling must remain in CSS so spacing follows the active theme.",
        )
        assertTrue(
            css.contains("-fx-padding: 10px 18px;"),
            "Canvas toolbar padding must be CSS-owned instead of hard-coded in Kotlin.",
        )
        assertTrue(
            css.contains(".canvas-tool-button {\n    -fx-min-width: 32px;"),
            "Canvas toolbar actions need consistent icon button dimensions.",
        )
        assertTrue(
            css.contains("-fx-content-display: graphic-only;"),
            "Canvas toolbar actions must stay icon-only with tooltips.",
        )
    }

    @Test
    fun `stylesheet exposes supported root font scale classes`() {
        val css = applicationCss()

        (10..24).forEach { size ->
            assertTrue(
                css.contains(".ui-font-$size { -fx-font-size: ${size}px; }"),
                "Application font size $size must be represented by a CSS class.",
            )
        }
    }

    @Test
    fun `application stylesheet is the only shipped app chrome stylesheet`() {
        val styleSheets =
            Files
                .list(Path.of("src/main/resources/styles"))
                .use { paths -> paths.map { it.fileName.toString() }.sorted().toList() }

        assertTrue(
            styleSheets.contains("application.css"),
            "The maintained application stylesheet must be shipped.",
        )
        assertFalse(
            styleSheets.contains("application_fixed.css"),
            "Stale alternate application stylesheets must not ship with outdated theme tokens.",
        )
    }

    private fun applicationCss(): String =
        requireNotNull(javaClass.getResourceAsStream("/styles/application.css")) {
            "Missing application stylesheet"
        }.bufferedReader().use { it.readText() }
}
