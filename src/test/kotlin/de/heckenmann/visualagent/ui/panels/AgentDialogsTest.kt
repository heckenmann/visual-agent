package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialog
import de.heckenmann.visualagent.ui.panels.AgentLogsDialog
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.DialogPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Window
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentDialogsTest {
    @Test
    fun `create dialog validates fields and returns normalized values`() =
        FxTestSupport.run {
            var saved: Triple<String, String, AgentConfig>? = null
            Platform.runLater {
                val pane = activeDialogPane()
                val textFields = descendants(pane).filterIsInstance<TextField>()
                val name = textFields.first()
                val role = descendants(pane).filterIsInstance<TextArea>().first()

                @Suppress("UNCHECKED_CAST")
                val template =
                    descendants(pane)
                        .filterIsInstance<ComboBox<*>>()
                        .first { it.items.contains("Tester") } as ComboBox<String>
                val save =
                    pane.buttonTypes
                        .map { pane.lookupButton(it) }
                        .filterIsInstance<Button>()
                        .first { it.text.contains("Create") }
                assertTrue(save.isDisable)
                name.text = "  Tester  "
                role.text = "  Validate releases  "
                template.selectionModel.select("Tester")
                assertTrue(!save.isDisable)
                save.fire()
            }

            AgentDetailsDialog.showFor { name, role, template ->
                saved = Triple(name, role, template)
            }

            assertEquals("Tester", saved?.first)
            assertEquals("Validate releases", saved?.second)
            assertEquals(AgentConfig.fromTemplate("tester"), saved?.third)
        }

    @Test
    fun `edit dialog preselects existing agent values`() =
        FxTestSupport.run {
            val agent = SubAgent.fromTemplate("agent-1", "Coder", "Implementation", "coder")
            var saved: Triple<String, String, AgentConfig>? = null
            Platform.runLater {
                val pane = activeDialogPane()
                val name = descendants(pane).filterIsInstance<TextField>().first()
                val role = descendants(pane).filterIsInstance<TextArea>().first()
                assertEquals("Coder", name.text)
                assertEquals("Implementation", role.text)
                pane.buttonTypes
                    .map { pane.lookupButton(it) }
                    .filterIsInstance<Button>()
                    .first { it.text.contains("Save") }
                    .fire()
            }

            AgentDetailsDialog.showFor(agent) { name, role, template ->
                saved = Triple(name, role, template)
            }

            assertEquals("Coder", saved?.first)
            assertEquals("Implementation", saved?.second)
            assertEquals(AgentConfig.fromTemplate("coder"), saved?.third)
        }

    @Test
    fun `dialog saves provider model and generation parameters`() =
        FxTestSupport.run {
            var saved: AgentConfig? = null
            Platform.runLater {
                val pane = activeDialogPane()
                val textFields = descendants(pane).filterIsInstance<TextField>()
                textFields[0].text = "Specialist"
                descendants(pane).filterIsInstance<TextArea>().first().text = "Use a dedicated model"

                @Suppress("UNCHECKED_CAST")
                val provider =
                    descendants(pane)
                        .filterIsInstance<ComboBox<*>>()
                        .first { it.items.contains("OpenAI") } as ComboBox<String>
                provider.selectionModel.select("OpenAI")
                val model =
                    descendants(pane)
                        .filterIsInstance<ComboBox<*>>()
                        .first { it.isEditable }
                model.editor.text = "gpt-agent"
                val parameterFields = textFields.filter { it.promptText == "Default" }
                parameterFields[0].text = "0.25"
                parameterFields[1].text = "0.8"
                parameterFields[2].text = "2048"
                pane.buttonTypes
                    .map { pane.lookupButton(it) }
                    .filterIsInstance<Button>()
                    .first { it.text.contains("Create") }
                    .fire()
            }

            AgentDetailsDialog.showFor { _, _, config -> saved = config }

            assertEquals("openai", saved?.provider)
            assertEquals("gpt-agent", saved?.model)
            assertEquals(0.25, saved?.temperature)
            assertEquals(0.8, saved?.topP)
            assertEquals(2048, saved?.maxTokens)
        }

    @Test
    fun `logs dialog renders empty and populated histories`() =
        FxTestSupport.run {
            val empty = SubAgent(id = "empty", name = "Idle", role = "Waiting")
            var captured = ""
            Platform.runLater {
                val pane = activeDialogPane()
                captured = descendants(pane).filterIsInstance<TextArea>().single().text
                closeDialog(pane)
            }
            AgentLogsDialog.showFor(empty)
            assertTrue(captured.contains("No activity"))

            val active =
                SubAgent(
                    id = "active",
                    name = "Coder",
                    role = "Implementation",
                    chatHistory = mutableListOf(Message("user", "Build"), Message("assistant", "Built")),
                )
            Platform.runLater {
                val pane = activeDialogPane()
                captured = descendants(pane).filterIsInstance<TextArea>().single().text
                closeDialog(pane)
            }
            AgentLogsDialog.showFor(active)
            assertTrue(captured.contains("USER\nBuild"))
            assertTrue(captured.contains("ASSISTANT\nBuilt"))
        }

    private fun activeDialogPane(): DialogPane =
        Window
            .getWindows()
            .filter { it.isShowing }
            .mapNotNull { it.scene?.root as? DialogPane }
            .last()

    private fun closeDialog(pane: DialogPane) {
        pane.buttonTypes
            .map { pane.lookupButton(it) }
            .filterIsInstance<Button>()
            .last()
            .fire()
    }

    private fun descendants(root: Parent): List<javafx.scene.Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is Parent) descendants(child) else emptyList()
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
