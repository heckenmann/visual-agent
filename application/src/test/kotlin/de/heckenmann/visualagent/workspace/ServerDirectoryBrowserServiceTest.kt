package de.heckenmann.visualagent.workspace

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Verifies that the direct-user server browser authorizes only short-lived opaque selections. */
class ServerDirectoryBrowserServiceTest {
    @Test
    fun `server browser resolves a selected directory only through its opaque selection id`() {
        val browser = ServerDirectoryBrowserService()
        val entry = browser.roots().entries.first()

        UUID.fromString(entry.selectionId)
        assertEquals(entry.location, browser.resolve(entry.selectionId).toString())
        assertFailsWith<IllegalArgumentException> { browser.resolve(entry.location) }
        assertTrue(entry.displayName.isNotBlank())
    }
}
