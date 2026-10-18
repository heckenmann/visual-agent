package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prevents the animated capability editor from stacking its labels and toggles on top of each other. */
class ModelCapabilitiesLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `capability labels and controls occupy separate rows`() = verifyLayout(420.dp, 1f)

    @Test
    fun `narrow capability editor wraps explanatory text without overlapping controls`() = verifyLayout(280.dp, 1f)

    @Test
    fun `larger text retains separate capability rows`() = verifyLayout(280.dp, 1.5f)

    private fun verifyLayout(
        width: Dp,
        fontScale: Float,
    ) {
        val model = mutableStateOf(ProviderModel("model", capabilities = setOf("chat"), capabilitiesComplete = true))
        compose.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.width(width)) {
                        ModelCapabilitiesEditor(model.value) { capability, supported ->
                            model.value =
                                model.value.copy(
                                    capabilityOverrides =
                                        model.value.capabilityOverrides + (capability.lowercase() to checkNotNull(supported)),
                                )
                        }
                    }
                }
            }
        }
        val labels =
            listOf(
                "Model capabilities",
                "Automatic uses provider evidence. Unknown is not the same as unsupported; each override affects only one capability.",
            ) + EDITABLE_MODEL_CAPABILITIES.map { it.replace('_', ' ') }
        val bounds = labels.map { label -> compose.onNodeWithText(label).getBoundsInRoot() }
        bounds.zipWithNext().forEachIndexed { index, (previous, next) ->
            assertTrue(next.top >= previous.bottom, "${labels[index]} overlaps ${labels[index + 1]}")
        }
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("TOOLS").performClick()
        compose.onNodeWithText("Supported").performClick()
        assertEquals(mapOf("tools" to true), model.value.capabilityOverrides)
    }
}
