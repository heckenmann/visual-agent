package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class WorkspaceRenderingCssTest {
    @Test
    fun `workspace chrome avoids expensive css effects`() {
        val css =
            requireNotNull(javaClass.getResourceAsStream("/styles/application.css")) {
                "Missing application stylesheet"
            }.bufferedReader().use { it.readText() }

        assertFalse(
            css.contains("-fx-effect"),
            "Workspace stylesheet must avoid JavaFX effects because they make large internal windows lag during drag and scroll.",
        )
    }
}
