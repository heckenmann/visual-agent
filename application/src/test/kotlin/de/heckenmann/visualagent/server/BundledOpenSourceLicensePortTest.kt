package de.heckenmann.visualagent.server

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/** Verifies the same bundled documents can be loaded by the desktop and executable JAR. */
class BundledOpenSourceLicensePortTest {
    @Test
    fun `loads project license and indexed third party materials from the application classpath`() {
        val documents = BundledOpenSourceLicensePort().documents()

        assertTrue(documents.any { it.component == "Visual Agent" && it.path == "LICENSE" })
        val libffi = documents.single { it.path == "jna-5.19.1/libffi-LICENSE" }
        assertContains(libffi.text, "deal in the Software without restriction")
        assertTrue(documents.any { it.path == "skiko-0.150.2/NOTICE" })
        assertTrue(documents.any { it.path == "dng-sdk-dbe0a676/LICENSE" })
        assertTrue(documents.any { it.path == "piex-bb217acd/LICENSE" })
        assertTrue(documents.any { it.path == "piex-bb217acd/NOTICE" })
        assertTrue(documents.any { it.path == "wuffs-e3f919cc/LICENSE" })
        assertTrue(documents.any { it.path == "liberation-fonts-2.1.5/LICENSE" })
        assertTrue(documents.none { it.path == "sources.json" })
    }
}
