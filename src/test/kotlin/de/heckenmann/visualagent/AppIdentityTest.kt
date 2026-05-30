package de.heckenmann.visualagent

import org.junit.jupiter.api.Test
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIdentityTest {
    @Test
    fun `process properties use visual agent display name`() {
        val previousAppleName = System.getProperty("apple.awt.application.name")
        val previousAboutName = System.getProperty("com.apple.mrj.application.apple.menu.about.name")
        val previousJavaFxName = System.getProperty("javafx.application.name")
        try {
            AppIdentity.configureProcessProperties()

            assertEquals(AppIdentity.DISPLAY_NAME, System.getProperty(AppIdentity.MAC_APPLICATION_NAME_PROPERTY))
            assertEquals(AppIdentity.DISPLAY_NAME, System.getProperty(AppIdentity.MAC_ABOUT_NAME_PROPERTY))
            assertEquals(AppIdentity.DISPLAY_NAME, System.getProperty(AppIdentity.JAVAFX_APPLICATION_NAME_PROPERTY))
        } finally {
            restoreProperty(AppIdentity.MAC_APPLICATION_NAME_PROPERTY, previousAppleName)
            restoreProperty(AppIdentity.MAC_ABOUT_NAME_PROPERTY, previousAboutName)
            restoreProperty(AppIdentity.JAVAFX_APPLICATION_NAME_PROPERTY, previousJavaFxName)
        }
    }

    @Test
    fun `application icon resource is available and readable`() {
        val iconUrl = assertNotNull(AppIdentity.iconUrl())
        val icon = ImageIO.read(iconUrl)

        assertNotNull(icon)
        assertTrue(icon.width >= 256)
        assertTrue(icon.height >= 256)
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
}
