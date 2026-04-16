package com.visualagent

import javafx.application.Application
import javafx.stage.Stage
import com.visualagent.ui.MainWindow

class Main : Application() {
    
    companion object {
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
