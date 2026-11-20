package de.heckenmann.visualagent.desktop

import kotlin.system.exitProcess

/** Desktop entry point kept separate from the server application entry point. */
object DesktopMain {
    /** Starts the desktop presentation host. */
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.firstOrNull() == "--check-startup-render-pattern") {
            val result =
                if (args.size == 2) inspectStartupPattern(args[1]) else StartupPatternResult.UNKNOWN
            println("Desktop startup rendering: visualIntegrity=${result.status}")
            exitProcess(result.exitCode)
        }
        runVisualAgentComposeApplication()
    }
}
