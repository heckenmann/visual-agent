package de.heckenmann.visualagent

import java.net.URL

/**
 * Central application identity used by Compose Desktop, macOS desktop integration, and build-time launch settings.
 *
 * Keeping the display name and icon path in one place avoids the common macOS fallback where the menu bar and
 * Dock show the generic JVM identity instead of the actual application name.
 */
object AppIdentity {
    /**
     * Human-readable application name shown in native menus, window titles, and the Dock.
     */
    const val DISPLAY_NAME: String = "Visual Agent"

    /**
     * Classpath resource path for the application icon.
     */
    const val ICON_RESOURCE: String = "/icons/visual-agent.png"

    /**
     * Legacy JVM system property used by older macOS Java integrations for the About menu name.
     */
    const val MAC_ABOUT_NAME_PROPERTY: String = "com.apple.mrj.application.apple.menu.about.name"

    /**
     * Resolve the classpath URL for the configured application icon.
     *
     * @return Icon URL, or null when the resource is missing from the classpath
     */
    fun iconUrl(): URL? = AppIdentity::class.java.getResource(ICON_RESOURCE)

    /**
     * Set JVM desktop identity properties before Compose initializes native desktop integration.
     *
     * This is intentionally safe to call more than once.
     */
    fun configureProcessProperties() {
        System.setProperty(MAC_ABOUT_NAME_PROPERTY, DISPLAY_NAME)
    }

    /**
     * Detect whether the current runtime is macOS.
     *
     * @param osName Operating system name to inspect; defaults to the current JVM property
     * @return true when the operating system name represents macOS
     */
    fun isMacOs(osName: String = System.getProperty("os.name", "")): Boolean = osName.contains("mac", ignoreCase = true)
}
