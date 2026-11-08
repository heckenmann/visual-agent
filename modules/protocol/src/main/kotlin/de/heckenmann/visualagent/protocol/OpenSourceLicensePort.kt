package de.heckenmann.visualagent.protocol

/** Loads the notices and license texts shipped with this application build. */
interface OpenSourceLicensePort {
    /** Returns recipient-facing license documents bundled in the current application package. */
    fun documents(): List<OpenSourceLicenseDocument>
}

/** One bundled license document associated with its component and package-relative source path. */
data class OpenSourceLicenseDocument(
    /** Component coordinates or first-party product name shown above the license text. */
    val component: String,
    /** Original path below the package's legal-material directory. */
    val path: String,
    /** Unmodified UTF-8 document text. */
    val text: String,
)
