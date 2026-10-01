package de.heckenmann.visualagent.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies that automated desktop startup remains explicitly opt-in. */
class DesktopStartupSmokeSupportTest {
    @Test
    fun `auto start is disabled unless the exact true value is provided`() {
        assertFalse(DesktopStartupSmokeSupport.autoStartRequested(null))
        assertFalse(DesktopStartupSmokeSupport.autoStartRequested(""))
        assertFalse(DesktopStartupSmokeSupport.autoStartRequested("yes"))
        assertFalse(DesktopStartupSmokeSupport.autoStartRequested("false"))
        assertTrue(DesktopStartupSmokeSupport.autoStartRequested("true"))
        assertTrue(DesktopStartupSmokeSupport.autoStartRequested("TRUE"))
    }
}
