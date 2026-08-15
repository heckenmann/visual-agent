package de.heckenmann.visualagent.desktop

import kotlin.test.Test
import kotlin.test.assertNotNull

/** Protects the dedicated desktop entry point from accidental removal. */
class DesktopMainTest {
    @Test
    fun `desktop main is available`() {
        assertNotNull(DesktopMain::class.java.getMethod("main", Array<String>::class.java))
    }
}
