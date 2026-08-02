package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [labelizeEnumName] and [PanelSelectOption] helpers.
 */
class ComposePanelControlsTest {
    @Test
    fun `labelizeEnumName title-cases snake_case enum names`() {
        assertEquals("Active", "ACTIVE".labelizeEnumName())
        assertEquals("Out Of Office", "OUT_OF_OFFICE".labelizeEnumName())
        assertEquals("Openai Compatible", "OPENAI_COMPATIBLE".labelizeEnumName())
    }

    @Test
    fun `labelizeEnumName collapses empty fragments`() {
        assertEquals("Two  Spaces", "TWO__SPACES".labelizeEnumName())
    }

    @Test
    fun `labelizeEnumName handles short fragments`() {
        assertEquals("Min T Two", "MIN_T_TWO".labelizeEnumName())
    }

    @Test
    fun `blank string stays empty`() {
        assertEquals("", "".labelizeEnumName())
    }

    @Test
    fun `panel select option preserves value and label`() {
        val option = PanelSelectOption(value = "openai", label = "OpenAI")

        assertEquals("openai", option.value)
        assertEquals("OpenAI", option.label)
    }

    @Test
    fun `panel select option data class considers value and label`() {
        val a = PanelSelectOption("x", "label-a")
        val b = PanelSelectOption("x", "different")

        assertEquals(a.copy(label = "different"), b)
    }

    @Test
    fun `optionForValue returns null for unknown value`() {
        assertNull(optionForValue("missing"))
    }

    private fun optionForValue(value: String): PanelSelectOption? =
        listOf(PanelSelectOption(value = "openai", label = "OpenAI")).firstOrNull { it.value == value }
}
