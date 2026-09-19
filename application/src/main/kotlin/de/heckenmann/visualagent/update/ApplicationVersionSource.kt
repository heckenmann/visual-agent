package de.heckenmann.visualagent.update

import org.springframework.stereotype.Component

/** Supplies the version embedded in the running Visual Agent distribution. */
internal fun interface ApplicationVersionSource {
    /** Return the normalized application version without a leading `v`. */
    fun currentVersion(): String
}

/** Reads the build-generated version resource packaged with the application. */
@Component
internal class PackagedApplicationVersionSource : ApplicationVersionSource {
    override fun currentVersion(): String =
        javaClass.classLoader
            .getResourceAsStream(VERSION_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.takeIf(String::isNotBlank)
            ?: error("The packaged Visual Agent version is unavailable")

    private companion object {
        const val VERSION_RESOURCE = "visual-agent-version.txt"
    }
}
