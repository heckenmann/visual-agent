package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.VisualAgentApplication
import javafx.application.Platform
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertTrue

class ApplicationGuiStartupTest {
    @Test
    fun `application starts with rendered main window`() {
        val context =
            SpringApplicationBuilder(VisualAgentApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties("visual-agent.ui.enabled=true")
                .run()
        try {
            val stageRef = AtomicReference<MainWindow>()
            val errorRef = AtomicReference<Throwable?>()
            val shownLatch = CountDownLatch(1)

            Platform.runLater {
                runCatching {
                    val mainWindow = context.getBean(MainWindow::class.java)
                    stageRef.set(mainWindow)
                    mainWindow.show()
                    shownLatch.countDown()
                }.onFailure { error ->
                    errorRef.set(error)
                    shownLatch.countDown()
                }
            }

            assertTrue(shownLatch.await(10, TimeUnit.SECONDS), "Main window was not shown in time")
            val startupError = errorRef.get()
            if (startupError != null) throw startupError
            val shownWindow = stageRef.get()
            assertTrue(shownWindow != null && shownWindow.isShowing, "Main window should be visible after startup")

            val hideLatch = CountDownLatch(1)
            Platform.runLater {
                shownWindow?.hide()
                hideLatch.countDown()
            }
            assertTrue(hideLatch.await(5, TimeUnit.SECONDS), "Main window hide did not complete in time")
        } finally {
            context.close()
        }
    }

    companion object {
        private var toolkitStarted = false

        @JvmStatic
        @BeforeAll
        fun startToolkit() {
            if (toolkitStarted) return
            val latch = CountDownLatch(1)
            runCatching {
                Platform.startup { latch.countDown() }
                assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit did not start in time")
            }.onFailure { error ->
                if (error !is IllegalStateException || error.message != "Toolkit already initialized") {
                    throw error
                }
            }
            toolkitStarted = true
        }

        @JvmStatic
        @AfterAll
        fun shutdownToolkit() {
            Platform.setImplicitExit(false)
        }
    }
}
