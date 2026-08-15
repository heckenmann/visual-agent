package de.heckenmann.visualagent.desktop

/** Desktop entry point kept separate from the server application entry point. */
object DesktopMain {
    /** Starts the desktop presentation host. */
    @JvmStatic
    fun main(args: Array<String>) {
        runVisualAgentComposeApplication()
    }
}
