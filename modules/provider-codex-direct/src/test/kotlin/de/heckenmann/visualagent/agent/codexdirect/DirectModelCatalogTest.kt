package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DirectModelCatalogTest {
    @Test
    fun `OAuth preserves account ordering and display names while excluding unlisted entries`() {
        val body =
            """
            {"models":[
              {"slug":"second","display_name":"Second model","visibility":"list"},
              {"slug":"hidden","visibility":"hide"},
              {"slug":"first","display_name":"First model","visibility":"list"}
            ]}
            """.trimIndent()

        val models = parseDirectModels(body, DirectProviderAuthMode.CHATGPT_OAUTH)

        assertEquals(listOf("second", "first"), models.map { it.id })
        assertEquals(listOf("Second model", "First model"), models.map { it.name })
        models.forEach { assertFalse(it.capabilitiesComplete) }
    }

    @Test
    fun `API key catalog uses platform identifiers and preserves their order`() {
        val models = parseDirectModels("""{"data":[{"id":"second"},{"id":"first"}]}""", DirectProviderAuthMode.API_KEY)

        assertEquals(listOf("second", "first"), models.map { it.id })
    }

    @Test
    fun `OAuth retains a reported context window without inventing other model metadata`() {
        val models =
            parseDirectModels(
                """{"models":[{"slug":"account-model","visibility":"list","context_window":98304}]}""",
                DirectProviderAuthMode.CHATGPT_OAUTH,
            )

        assertEquals(98304, models.single().contextLimit)
        assertNull(models.single().outputLimit)
        assertEquals(emptySet(), models.single().capabilities)
        assertFalse(models.single().capabilitiesComplete)
    }

    @Test
    fun `absent or invalid context windows remain unknown`() {
        listOf(
            "",
            ",\"context_window\":null",
            ",\"context_window\":0",
            ",\"context_window\":-1",
            ",\"context_window\":\"98304\"",
            ",\"context_window\":1.5",
            ",\"context_window\":999999999999",
            ",\"context_window\":{}",
        ).forEach { field ->
            val models =
                parseDirectModels(
                    """{"models":[{"slug":"account-model","visibility":"list"$field}]}""",
                    DirectProviderAuthMode.CHATGPT_OAUTH,
                )

            assertNull(models.single().contextLimit)
        }
    }

    @Test
    fun `catalog mismatch never silently changes the selected authentication route`() {
        assertFailsWith<NoSuchElementException> {
            parseDirectModels("""{"data":[]}""", DirectProviderAuthMode.CHATGPT_OAUTH)
        }
        assertFailsWith<NoSuchElementException> {
            parseDirectModels("""{"models":[]}""", DirectProviderAuthMode.API_KEY)
        }
    }
}
