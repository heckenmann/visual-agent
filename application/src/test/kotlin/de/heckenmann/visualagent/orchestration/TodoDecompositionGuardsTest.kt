package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.todo.TodoManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises late analyst responses and non-progressing decomposition through the planner. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoDecompositionGuardsTest {
    private val description = "Design the architecture and integrate the complete pipeline"

    @Test
    fun `still complex children are executable leaves rather than new analysis roots`() =
        runTest {
            val manager = TodoManager()
            val parent = manager.add(description)
            manager.replaceWithChildren(parent, listOf(description))
            val leaf = manager.getPending().single()
            val planner = planner(manager, Mono.error(IllegalStateException("Leaf must not call the analyst")))
            assertFalse(planner.expandComplexTodo(leaf))
            assertEquals(1, leaf.decompositionDepth)
            assertEquals(listOf(leaf), manager.getPending())
        }

    @Test
    fun `equivalent single child falls back to the original task`() =
        runTest {
            val manager = TodoManager()
            val parent = manager.add(description)
            val response = Mono.just(response("- Design   the architecture and integrate the complete pipeline"))
            assertFalse(planner(manager, response).expandComplexTodo(parent))
            assertEquals(listOf(parent), manager.getAll())
        }

    @Test
    fun `late analyst response cannot replace edited cancelled or deleted parent`() =
        runTest {
            for (mutation in listOf<(TodoManager, String) -> Unit>(
                { manager, id -> manager.update(id, "Changed objective") },
                { manager, id -> manager.cancelTodo(id) },
                { manager, id -> manager.remove(id) },
            )) {
                val manager = TodoManager()
                val parent = manager.add(description)
                val gate = Sinks.one<ChatResponse>()
                val planner = planner(manager, gate.asMono())
                val work = async { planner.expandComplexTodo(parent) }
                runCurrent()
                mutation(manager, parent.id)
                val expected = manager.getAll()
                gate.tryEmitValue(response("- Inspect modules\n- Implement pipeline"))
                assertFalse(work.await())
                assertEquals(expected, manager.getAll())
            }
        }

    private fun planner(
        manager: TodoManager,
        response: Mono<ChatResponse>,
    ): AutonomousTaskPlanner {
        val analyst = SubAgent(id = "analyst", name = "Analyst", role = "Analysis")
        val provider = mockk<LLMProvider>()
        val tools = mockk<AgentToolConfigService>()
        every { tools.toolsFor(analyst) } returns emptySet()
        every { provider.chatReactive(any<ChatRequestContext>()) } returns response
        return AutonomousTaskPlanner(manager, mapOf(analyst.id to analyst), provider, tools)
    }

    private fun response(content: String) = ChatResponse("test", Message("assistant", content), true)
}
