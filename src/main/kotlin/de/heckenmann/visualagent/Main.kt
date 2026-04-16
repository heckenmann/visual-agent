package de.heckenmann.visualagent

import de.heckenmann.visualagent.ui.MainWindow
import javafx.application.Application
import javafx.stage.Stage

/**
 * Main entry point for the Visual Agent application.
 *
 * This JavaFX application provides a coding agent interface with:
 * - SubAgent management
 * - Chat interface
 * - Todo list
 * - Canvas for visual output
 * - Knowledge database
 *
 * @see MainWindow for the main UI layout
 */
class Main : Application() {

    companion object {
        /**
         * Application entry point.
         *
         * @param args Command line arguments
         */
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(Main::class.java, *args)
        }
    }

    override fun start(primaryStage: Stage) {
        val mainWindow = MainWindow()
        mainWindow.show()
    }
}
