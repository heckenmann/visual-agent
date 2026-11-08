package de.heckenmann.visualagent.protocol

/** Read-only local metadata for the currently running application and runtime. */
interface ApplicationInformationPort {
    /** Returns local build/runtime information without making a network request. */
    fun current(): ApplicationInformation
}

/** Non-sensitive runtime details displayed for support and diagnostics. */
data class ApplicationInformation(
    /** Version embedded by the build and shared with update detection. */
    val version: String,
    /** Java runtime vendor/name, when available. */
    val runtimeName: String,
    /** Java runtime version. */
    val runtimeVersion: String,
    /** Operating system name. */
    val operatingSystem: String,
    /** Operating system version. */
    val operatingSystemVersion: String,
    /** Process architecture. */
    val architecture: String,
)
