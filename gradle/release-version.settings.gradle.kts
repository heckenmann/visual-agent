// Override the shared catalog before any module reads it. Gradle tracks the input
// through its Provider API; no catalog rewrite, Git lookup or CI-only source edit is needed.
val releaseTag = providers.gradleProperty("releaseTag").orNull
if (!releaseTag.isNullOrEmpty()) {
    val releaseVersion = releaseTag.removePrefix("v")
    val numeric = "(0|[1-9][0-9]*)"
    val prereleaseIdentifier = "(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
    val semanticVersion = Regex(
        "$numeric\\.$numeric\\.$numeric" +
            "(?:-$prereleaseIdentifier(?:\\.$prereleaseIdentifier)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?",
    )
    require(semanticVersion.matches(releaseVersion)) {
        "Release tag must be a semantic version such as v1.2.3 or v1.2.3-beta.1: $releaseTag"
    }
    val components = releaseVersion.substringBefore('-').substringBefore('+').split('.').map { it.toLongOrNull() }
    require(components[0] in 0L..255L && components[1] in 0L..255L && components[2] in 0L..65535L) {
        "Release version exceeds native installer limits (major/minor <= 255, patch <= 65535): $releaseTag"
    }
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                version("visual-agent", releaseVersion)
            }
        }
    }
}
