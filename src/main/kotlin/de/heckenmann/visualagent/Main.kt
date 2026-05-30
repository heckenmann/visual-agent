package de.heckenmann.visualagent

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.MainWindow
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/**
 * Main entry point for the Visual Agent application with Spring Boot integration.
 */
class Main : Application() {

    private lateinit var springContext: ConfigurableApplicationContext

    override fun init() {
        AppIdentity.configureProcessProperties()

        // Force Spring Boot to use the Java logging system in this desktop app runtime
        // to avoid Logback native compatibility issues on some environments.
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "org.springframework.boot.logging.java.JavaLoggingSystem")

        // Desktop application doesn't need a web server; disable web environment to avoid
        // reactive/server autoconfiguration attempting to start.
        springContext = SpringApplicationBuilder(VisualAgentApplication::class.java)
            .web(org.springframework.boot.WebApplicationType.NONE)
            .run()
    }

    override fun start(primaryStage: Stage) {
        context = springContext
        Application.setUserAgentStylesheet(AppConfig.instance.getThemeStylesheet())
        // Retrieve the MainWindow bean on the FX application thread to satisfy JavaFX
        // thread checks. The bean is marked @Lazy so Spring will construct it only now.
        val mainWindow = springContext.getBean(MainWindow::class.java)
        Platform.runLater {
            mainWindow.show()
        }
    }

    override fun stop() {
        springContext.close()
    }

    companion object {
        lateinit var context: ConfigurableApplicationContext

        @JvmStatic
        fun main(args: Array<String>) {
            AppIdentity.configureProcessProperties()
            Application.launch(Main::class.java, *args)
        }
    }
}
