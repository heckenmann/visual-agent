package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import de.heckenmann.visualagent.ui.panels.SubAgentCardView
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubAgentsPanelInteractionTest {
    @Test
    fun `panel renders workload summaries and status changes`() =
        FxTestSupport.run {
            val panel = SubAgentsPanel()
            val coder = SubAgent(id = "coder", name = "Coder", role = "Implementation")
            val researcher = SubAgent(id = "researcher", name = "Researcher", role = "Research")

            panel.setAgents(listOf(coder, researcher), mapOf("coder" to 2))
            assertEquals("2 agents", panel.field<Label>("agentCountLabel").text)
            assertEquals("2 active jobs", panel.field<Label>("activeJobsLabel").text)
            assertFalse(panel.field<VBox>("emptyState").isVisible)

            panel.updateAgentStatus("researcher", AgentStatus.BUSY, "Investigate", 1)
            assertEquals("3 active jobs", panel.field<Label>("activeJobsLabel").text)
            panel.updateAgentStatus("missing", AgentStatus.OFFLINE)

            panel.setAgents(emptyList())
            assertEquals("0 agents", panel.field<Label>("agentCountLabel").text)
            assertTrue(panel.field<VBox>("emptyState").isVisible)
            assertTrue(panel.field<Button>("emptyCreateAgentButton").isVisible)
            assertTrue(panel.field<Button>("emptyCreateAgentButton").onAction != null)

            panel.resize(900.0, 600.0)
            panel.layout()
            assertEquals(900.0, panel.field<VBox>("rootVBox").width)
        }

    @Test
    fun `card updates status display and delegates quick actions`() =
        FxTestSupport.run {
            val agent = SubAgent(id = "agent-1", name = "Worker", role = "General")
            val card = SubAgentCardView(agent, activeJobCount = -2)
            val actions = mutableListOf<String>()
            card.onConfigure = { actions += "configure" }
            card.onRun = { actions += "run" }
            card.onLogs = { actions += "logs" }
            card.onDelete = { actions += "delete" }

            card.updateStatus(AgentStatus.BUSY, "A very important task", 3)
            assertEquals(3, card.activeJobCount)
            assertEquals("BUSY", card.field<Label>("statusIndicator").text)
            assertEquals("Jobs: 3", card.field<Label>("jobsLabel").text)
            assertTrue(card.field<Label>("statusIndicator").styleClass.contains("agent-status-busy"))

            card.updateStatus(AgentStatus.OFFLINE, " ")
            assertEquals("Waiting for work", card.field<Label>("taskLabel").text)
            assertTrue(card.field<Label>("statusIndicator").styleClass.contains("agent-status-offline"))

            agent.name = "Renamed"
            agent.role = "Specialist"
            card.refreshDisplay()
            assertEquals("Renamed", card.field<Label>("nameLabel").text)
            assertEquals("Specialist", card.field<Label>("roleLabel").text)

            listOf("btnConfigure", "btnRun", "btnLogs", "btnDelete").forEach { name ->
                card.field<Button>(name).fire()
            }
            assertEquals(listOf("configure", "run", "logs", "delete"), actions)

            card.resize(420.0, 240.0)
            card.layout()
            assertEquals(420.0, card.field<VBox>("root").width)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.field(name: String): T {
        val field =
            generateSequence(this::class.java) { it.superclass }
                .mapNotNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
                .first()
        field.isAccessible = true
        return field.get(this) as T
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
