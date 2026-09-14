package de.heckenmann.visualagent.desktop

import net.harawata.appdirs.AppDirsFactory
import java.nio.file.Path

/** Resolves client-owned bootstrap configuration before a Visual Agent server is connected. */
internal object ClientConfigPathResolver {
    private const val APPLICATION_NAME = "Visual Agent"
    private const val VENDOR_NAME = "de.heckenmann"
    private const val BOOKMARK_FILE = "startup-servers.json"
    private const val CLIENT_CONFIG_ROOT_PROPERTY = "visual-agent.client.config-root"

    /** Returns the current client-local server bookmark file. */
    fun bookmarkFile(): Path = resolveBookmarkFile(System.getProperty(CLIENT_CONFIG_ROOT_PROPERTY))

    /** Resolves the bookmark file below an explicit client config root or the platform default. */
    fun resolveBookmarkFile(configuredRoot: String?): Path {
        val root =
            configuredRoot
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(Path::of)
                ?: Path.of(AppDirsFactory.getInstance().getUserConfigDir(APPLICATION_NAME, null, VENDOR_NAME))
        return root.resolve(BOOKMARK_FILE).toAbsolutePath().normalize()
    }

    /** Returns the pre-#322 bookmark location used by older desktop installations. */
    fun legacyBookmarkFile(): Path =
        Path
            .of(AppDirsFactory.getInstance().getUserDataDir(APPLICATION_NAME, null, VENDOR_NAME))
            .resolve(BOOKMARK_FILE)
            .toAbsolutePath()
            .normalize()
}
