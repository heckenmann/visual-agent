package de.heckenmann.visualagent.ui.workspace

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies that the optional UI scale is applied relative to the platform density. */
class ComposeWorkspaceUiScaleTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `manual UI scale multiplies the platform density`() {
        var platformDensity = 0f
        var appliedDensity = 0f

        composeTestRule.setContent {
            platformDensity = LocalDensity.current.density
            ApplyVisualAgentUiScale(scalePercent = 150) {
                appliedDensity = LocalDensity.current.density
            }
        }

        assertEquals(platformDensity * 1.5f, appliedDensity, 0.001f)
    }

    @Test
    fun `automatic UI scale preserves the platform density`() {
        var platformDensity = 0f
        var appliedDensity = 0f

        composeTestRule.setContent {
            platformDensity = LocalDensity.current.density
            ApplyVisualAgentUiScale(scalePercent = null) {
                appliedDensity = LocalDensity.current.density
            }
        }

        assertEquals(platformDensity, appliedDensity, 0.001f)
    }
}
