package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeShortcutsAndFiltersTest {
    @Test
    fun `workspace shortcut digit maps one through six`() {
        assertEquals("chat", panelIdForShortcutDigit(1))
        assertEquals("canvas", panelIdForShortcutDigit(6))
        assertNull(panelIdForShortcutDigit(0))
        assertNull(panelIdForShortcutDigit(7))
    }

    @Test
    fun `filter commands matches id title and description`() {
        val commands =
            listOf(
                ComposeCommand("open-chat", "Chat", "Open chat panel") {},
                ComposeCommand("open-files", "Files", "Open file panel") {},
            )
        assertEquals(2, filterCommands(commands, "").size)
        assertEquals(1, filterCommands(commands, "chat").size)
        assertEquals("open-chat", filterCommands(commands, "chat").single().id)
        assertEquals(1, filterCommands(commands, "file panel").size)
        assertEquals(0, filterCommands(commands, "xyz").size)
    }

    @Test
    fun `labelize enum name formats snake and lowercase`() {
        assertEquals("Openai Compatible", "OPENAI_COMPATIBLE".labelizeEnumName())
        assertEquals("In Progress", "IN_PROGRESS".labelizeEnumName())
    }
}

class ComposeProviderDisplayNamesTest {
    @Test
    fun `provider display name includes id`() {
        val profile =
            ProviderProfile(
                id = "openai",
                name = "OpenAI",
                adapter = de.heckenmann.visualagent.agent.provider.ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://x",
            )
        assertEquals("OpenAI (openai)", profile.providerDisplayName())
    }

    @Test
    fun `model display name uses name when different from id`() {
        val model = ProviderModelConfig(id = "gpt-4o", name = "GPT-4o")
        assertEquals("GPT-4o (gpt-4o)", model.modelDisplayName())
    }
}

class ComposeSubAgentDetailsEditorHelpersTest {
    @Test
    fun `options map text conversion round trips`() {
        val map = mapOf("b" to "2", "a" to "1")
        assertEquals("a=1\nb=2", map.toOptionsText())
        assertEquals(map, "b=2\na=1".toOptionsMapOrNull())
        assertNull("noequals".toOptionsMapOrNull())
        assertNull("=value".toOptionsMapOrNull())
    }

    @Test
    fun `optional numeric validators accept blank or valid numbers`() {
        assertTrue("".optionalDoubleIsValid())
        assertTrue("1.5".optionalDoubleIsValid())
        assertFalse("x".optionalDoubleIsValid())
        assertTrue("".optionalIntIsValid())
        assertTrue("42".optionalIntIsValid())
        assertFalse("x".optionalIntIsValid())
    }
}
