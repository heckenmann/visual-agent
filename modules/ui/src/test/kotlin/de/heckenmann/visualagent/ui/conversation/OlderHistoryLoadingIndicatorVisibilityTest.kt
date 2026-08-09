@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies visibility of the older-history loading indicator.
 */
class OlderHistoryLoadingIndicatorVisibilityTest {
    @Test
    fun `hides loading indicator when older history is exhausted`() {
        assertFalse(
            shouldShowOlderHistoryLoadingIndicator(
                isLoadingOlder = true,
                hasMoreHistory = false,
            ),
        )
        assertTrue(
            shouldShowOlderHistoryLoadingIndicator(
                isLoadingOlder = true,
                hasMoreHistory = true,
            ),
        )
    }
}
