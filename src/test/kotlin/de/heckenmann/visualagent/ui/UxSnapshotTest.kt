package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.VisualAgentApplication
import de.heckenmann.visualagent.config.AppThemeStylesheets
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.Button
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.test.assertTrue

class UxSnapshotTest {
    @Test
    fun `renders snapshots for every primary workspace panel`() {
        val context =
            SpringApplicationBuilder(VisualAgentApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties("visual-agent.ui.enabled=true")
                .run()
        val outputDirectory = Path.of("build", "reports", "ux")
        Files.createDirectories(outputDirectory)
        val error = AtomicReference<Throwable?>()
        val rendered = CountDownLatch(1)

        try {
            Platform.runLater {
                runCatching {
                    val window = context.getBean(MainWindow::class.java)
                    window.width = 1180.0
                    window.height = 780.0
                    window.show()
                    snapshot(window, outputDirectory.resolve("conversation.png"))
                    snapshotPanel(window, "#sessionBtn", outputDirectory.resolve("session.png"))
                    snapshotPanel(window, "#agentsBtn", outputDirectory.resolve("agents.png"))
                    snapshotPanel(window, "#planBtn", outputDirectory.resolve("todos.png"))
                    snapshotPanel(window, "#canvasBtn", outputDirectory.resolve("canvas.png"))
                    snapshotPanel(window, "#settingsBtn", outputDirectory.resolve("settings.png"))
                    window.hide()
                    window.width = 960.0
                    window.height = 680.0
                    window.show()
                    snapshotPanel(window, "#conversationBtn", outputDirectory.resolve("compact-conversation.png"))
                    snapshotPanel(window, "#sessionBtn", outputDirectory.resolve("compact-session.png"))
                    snapshotPanel(window, "#canvasBtn", outputDirectory.resolve("compact-canvas.png"))
                    val currentStylesheet = Application.getUserAgentStylesheet()
                    Application.setUserAgentStylesheet(AppThemeStylesheets.stylesheetFor("Primer Dark"))
                    window.hide()
                    window.width = 1180.0
                    window.height = 780.0
                    window.show()
                    snapshotPanel(window, "#conversationBtn", outputDirectory.resolve("dark-conversation.png"))
                    snapshotPanel(window, "#sessionBtn", outputDirectory.resolve("dark-session.png"))
                    snapshotPanel(window, "#agentsBtn", outputDirectory.resolve("dark-agents.png"))
                    snapshotPanel(window, "#canvasBtn", outputDirectory.resolve("dark-canvas.png"))
                    Application.setUserAgentStylesheet(currentStylesheet)
                    context.getBean(ChatPanel::class.java).clearMessages()
                    snapshotPanel(window, "#conversationBtn", outputDirectory.resolve("empty-conversation.png"))
                    context.getBean(SubAgentsPanel::class.java).setAgents(emptyList())
                    snapshotPanel(window, "#agentsBtn", outputDirectory.resolve("empty-agents.png"))
                    window.hide()
                }.onFailure(error::set)
                rendered.countDown()
            }

            assertTrue(rendered.await(20, TimeUnit.SECONDS), "UX snapshots were not rendered in time")
            error.get()?.let { throw it }
            assertTrue(Files.size(outputDirectory.resolve("conversation.png")) > 0)
        } finally {
            context.close()
        }
    }

    private fun snapshotPanel(
        window: MainWindow,
        buttonSelector: String,
        destination: Path,
    ) {
        (window.scene.lookup(buttonSelector) as Button).fire()
        snapshot(window, destination)
    }

    private fun snapshot(
        window: MainWindow,
        destination: Path,
    ) {
        window.scene.root.applyCss()
        window.scene.root.layout()
        val image = window.scene.snapshot(null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", destination.toFile())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() {
            val latch = CountDownLatch(1)
            runCatching {
                Platform.startup(latch::countDown)
                assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit did not start in time")
            }.onFailure { error ->
                if (error !is IllegalStateException || error.message != "Toolkit already initialized") throw error
            }
            Platform.setImplicitExit(false)
        }

        @JvmStatic
        @AfterAll
        fun keepToolkitAvailable() {
            Platform.setImplicitExit(false)
        }
    }
}
