package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.todo.TodoManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AutonomousTaskPlannerTest {
    @Test
    fun `does not create a dynamic worker when no agents exist`() {
        val todoManager = TodoManager()
        todoManager.add("Add focused tests for the queue")
        val planner = planner(todoManager, mutableMapOf())

        val worker = planner.selectWorkerAgentForNextTodo()

        assertNull(worker)
    }

    @Test
    fun `waits when all configured agents are busy`() {
        val todoManager = TodoManager()
        todoManager.add("Implement the feature")
        val busyAgent = SubAgent(id = "coder", name = "Coder", role = "Implementation", status = AgentStatus.BUSY)
        val planner = planner(todoManager, mutableMapOf(busyAgent.id to busyAgent))

        assertNull(planner.selectWorkerAgentForNextTodo())
    }

    @Test
    fun `selects an idle specialist before a general worker`() {
        val todoManager = TodoManager()
        todoManager.add("Research the API docs")
        val general = SubAgent(id = "worker", name = "Worker", role = "General")
        val researcher = SubAgent(id = "researcher", name = "Researcher", role = "Research")
        val agents = linkedMapOf(general.id to general, researcher.id to researcher)
        val planner = planner(todoManager, agents)

        assertSame(researcher, planner.selectWorkerAgentForNextTodo())
    }

    @Test
    fun `detects complex descriptions`() {
        val planner = planner(TodoManager(), mutableMapOf())

        assertTrue(planner.isComplex("Design the architecture and integrate the complete pipeline"))
        assertTrue(
            planner.isComplex(
                "Create a detailed implementation plan that covers validation persistence retries observability " +
                    "documentation migration compatibility rollout monitoring and recovery behavior",
            ),
        )
        assertFalse(planner.isComplex("Fix typo"))
    }

    @Test
    fun `expands a complex todo into actionable subtasks`() =
        runTest {
            val todoManager = TodoManager()
            todoManager.add("Design the architecture and integrate the complete pipeline")
            val analyst = SubAgent(id = "analyst", name = "Analyst", role = "Analysis")
            val provider = mockk<LLMProvider>()
            val toolConfig = mockk<AgentToolConfigService>()
            every { toolConfig.toolsFor(analyst) } returns emptySet()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(response("- Inspect modules\n- Implement pipeline"))
            val planner =
                planner(
                    todoManager = todoManager,
                    agents = mutableMapOf(analyst.id to analyst),
                    provider = provider,
                    toolConfig = toolConfig,
                )

            assertTrue(planner.expandComplexTodoIfNeeded(todoManager.getAll()))
            assertEquals(listOf("Inspect modules", "Implement pipeline"), todoManager.getPending().map { it.description })
        }

    @Test
    fun `does not decompose complex todo without an existing analyst`() =
        runTest {
            val todoManager = TodoManager()
            val original = todoManager.add("Design the architecture and integrate the complete pipeline")
            val planner = planner(todoManager, mutableMapOf())

            assertFalse(planner.expandComplexTodoIfNeeded(todoManager.getAll()))
            assertSame(original, todoManager.getById(original.id))
        }

    @Test
    fun `reviews worker output and builds complete instructions`() =
        runTest {
            val provider = mockk<LLMProvider>()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(response("APPROVED\nLooks good."))
            val planner =
                planner(
                    todoManager = TodoManager(),
                    agents = mutableMapOf(),
                    provider = provider,
                )

            assertTrue(planner.reviewWorkerResult("todo-1", "Implement", "Done", "You are the main orchestrator agent."))
            assertTrue(planner.reviewWorkerResult("todo-1", "Implement", "", "You are the main orchestrator agent."))
            val instruction =
                planner.buildWorkerInstruction(
                    TodoManager().add("Implement persistence"),
                )
            assertTrue(instruction.contains("Objective: Implement persistence"))
            assertTrue(instruction.contains("Deliverable requirements"))
        }

    @Test
    fun `ux seeds remain complete and ordered`() {
        val seeds = UxSeedTasks.all()

        assertEquals(19, seeds.size)
        assertTrue(seeds.first().startsWith("ChatPanel"))
        assertTrue(seeds.last().contains("accessibility"))
    }

    private fun planner(
        todoManager: TodoManager,
        agents: MutableMap<String, SubAgent>,
        provider: LLMProvider = mockk(),
        toolConfig: AgentToolConfigService = mockk(),
    ) = AutonomousTaskPlanner(
        todoManager = todoManager,
        subAgents = agents,
        llmProvider = provider,
        agentToolConfigService = toolConfig,
    )

    private fun response(content: String) =
        ChatResponse(
            model = "test",
            message = Message("assistant", content),
            done = true,
        )
}
