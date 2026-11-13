package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.update.ApplicationVersionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies About metadata uses the same local version source as update detection. */
class RunningApplicationInformationPortTest {
    @Test
    fun `uses packaged version and exposes only support runtime metadata`() {
        val versionSource = ApplicationVersionSource { "1.2.3-beta.1" }

        val information = RunningApplicationInformationPort(versionSource).current()

        assertEquals(versionSource.currentVersion(), information.version)
        assertTrue(information.runtimeName.isNotBlank())
        assertTrue(information.runtimeVersion.isNotBlank())
        assertTrue(information.operatingSystem.isNotBlank())
        assertTrue(information.architecture.isNotBlank())
        assertFalse(information.toString().contains(System.getProperty("user.home")))
    }
}
