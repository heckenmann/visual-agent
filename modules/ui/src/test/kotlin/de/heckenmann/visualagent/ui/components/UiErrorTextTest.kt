package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.UserFacingError
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that UI status text never exposes raw backend exception messages. */
class UiErrorTextTest {
    @Test
    fun `safe protocol error is rendered`() {
        val error =
            ProtocolOperationException(
                UserFacingError(ProtocolErrorCategory.WORKSPACE, "Import failed", "Choose a smaller file."),
            )

        assertEquals("Import failed: Choose a smaller file.", error.toUiErrorMessage())
    }

    @Test
    fun `unknown exception is reduced to generic status`() {
        assertEquals("Operation failed", IllegalStateException("response body contains a secret").toUiErrorMessage())
    }
}
