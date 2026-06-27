package de.heckenmann.visualagent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIdentityTest {
    @Test
    fun `process properties use visual agent display name`() {
        val previousAboutName = System.getProperty("com.apple.mrj.application.apple.menu.about.name")
        try {
            AppIdentity.configureProcessProperties()

            assertEquals(AppIdentity.DISPLAY_NAME, System.getProperty(AppIdentity.MAC_ABOUT_NAME_PROPERTY))
        } finally {
            restoreProperty(AppIdentity.MAC_ABOUT_NAME_PROPERTY, previousAboutName)
        }
    }

    @Test
    fun `application icon resource is available and readable`() {
        val iconUrl = assertNotNull(AppIdentity.iconUrl())
        val bytes = iconUrl.openStream().use { it.readBytes() }

        assertTrue(bytes.take(8).toByteArray().contentEquals(PNG_SIGNATURE))
        assertTrue(bytes.readPngInt(16) >= 256)
        assertTrue(bytes.readPngInt(20) >= 256)
    }

    @Test
    fun `mac os detection accepts darwin style names`() {
        assertTrue(AppIdentity.isMacOs("Mac OS X"))
        assertTrue(AppIdentity.isMacOs("macOS"))
    }

    private fun restoreProperty(
        name: String,
        value: String?,
    ) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }

    private fun ByteArray.readPngInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
