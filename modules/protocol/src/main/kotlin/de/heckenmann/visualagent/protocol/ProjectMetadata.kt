package de.heckenmann.visualagent.protocol

/** Canonical public identity and links for the Visual Agent project. */
object ProjectMetadata {
    /** Application name used by the UI and bundled legal notices. */
    const val DISPLAY_NAME: String = "Visual Agent"

    /** Repository owner name shown in project information. */
    const val PROJECT_OWNER: String = "heckenmann"

    /** Public profile for the repository owner. */
    const val OWNER_URL: String = "https://github.com/$PROJECT_OWNER"

    /** Short public description of the project. */
    const val DESCRIPTION: String =
        "A Kotlin desktop application that helps models visualize their output with a canvas, workspace files, and agent tools."

    /** Canonical GitHub repository URL. */
    const val REPOSITORY_URL: String = "https://github.com/heckenmann/visual-agent"

    /** GitHub issue tracker URL. */
    const val ISSUES_URL: String = "$REPOSITORY_URL/issues"

    /** GitHub release and changelog URL. */
    const val RELEASES_URL: String = "$REPOSITORY_URL/releases"

    /** Project README URL. */
    const val README_URL: String = "$REPOSITORY_URL/blob/master/README.md"

    /** Project Code of Conduct URL. */
    const val CODE_OF_CONDUCT_URL: String = "$REPOSITORY_URL/blob/master/CODE_OF_CONDUCT.md"

    /** Links to primary project resources shown in the About panel. */
    fun projectLinks(): List<ProjectResourceLink> =
        listOf(
            ProjectResourceLink("Repository", REPOSITORY_URL),
            ProjectResourceLink("Project owner", OWNER_URL),
            ProjectResourceLink("Issue tracker", ISSUES_URL),
            ProjectResourceLink("Releases and changelog", RELEASES_URL),
            ProjectResourceLink("README and documentation", README_URL),
        )

    /** Community documents that currently exist in the repository. */
    fun communityLinks(): List<ProjectResourceLink> = listOf(ProjectResourceLink("Code of Conduct", CODE_OF_CONDUCT_URL))
}

/** A public project resource that can be opened from the About panel. */
data class ProjectResourceLink(
    /** Short user-facing resource name. */
    val title: String,
    /** Canonical HTTPS URL. */
    val url: String,
)
