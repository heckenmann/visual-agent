package de.heckenmann.visualagent.agent.context

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MainSystemPromptComposerTest {
    private val emptyTodos: List<Todo> = emptyList()
    private val toolConfigService = AgentToolConfigService(MapSubAgentConfigStore())

    @Test
    fun `prompt contains explicit tool set boundary`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("Your Available Tools" in prompt)
        assertTrue("agent:list" in prompt)
        assertTrue("agent:show" in prompt)
        assertTrue("agent:create" in prompt)
        assertTrue("agent:update" in prompt)
        assertTrue("agent:delete" in prompt)
        assertTrue("agent:log" in prompt)
        assertTrue("todos" in prompt)
        assertTrue("do NOT have access to" in prompt)
        assertTrue("file:" in prompt)
        assertTrue("terminal" in prompt)
        assertTrue("browser" in prompt)
        assertTrue("search" in prompt)
        assertTrue("canvas" in prompt)
        assertTrue("history" in prompt)
    }

    @Test
    fun `prompt instructs to discover sub-agents via agent list`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("agent:list" in prompt)
        assertTrue("agent:show" in prompt)
        assertTrue("Discovering and Creating Sub-Agents" in prompt || "Discovering" in prompt)
    }

    @Test
    fun `prompt contains delegation decision tree`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("When to Delegate" in prompt || "When to delegate" in prompt)
        assertTrue("Answer directly" in prompt)
        assertTrue("File operations" in prompt)
        assertTrue("Terminal commands" in prompt)
        assertTrue("Browser or search" in prompt)
        assertTrue("Canvas operations" in prompt)
        assertTrue("Research or analysis" in prompt)
        assertTrue("History search" in prompt)
    }

    @Test
    fun `prompt contains history search instruction`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("Missing Information" in prompt || "Missing information" in prompt)
        assertTrue("history" in prompt)
        assertTrue("search" in prompt)
    }

    @Test
    fun `prompt contains failure recovery rules`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("Failure Handling" in prompt || "Failure handling" in prompt)
        assertTrue("retry" in prompt)
        assertTrue("twice" in prompt)
        assertTrue("I cannot do this" in prompt)
    }

    @Test
    fun `prompt contains todo-driven execution guidance`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("Todo Workflow" in prompt || "Todo workflow" in prompt)
        assertTrue("non-trivial" in prompt)
        assertTrue("assignedAgentId" in prompt)
    }

    @Test
    fun `prompt explains auto-pickup of todos`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("automatically set to PENDING" in prompt)
        assertTrue("autonomous coordinator" in prompt)
        assertTrue("automatically pick up" in prompt)
        assertTrue("do NOT need to manually start" in prompt)
    }

    @Test
    fun `prompt explains auto-notification on todo completion`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("completes or cancels" in prompt)
        assertTrue("notification appears" in prompt)
        assertTrue("automatically prompted" in prompt)
    }

    @Test
    fun `prompt contains todo summary with counters`() {
        val todos =
            listOf(
                Todo(id = "1", description = "Task A", status = TodoStatus.PENDING, position = 0),
                Todo(id = "2", description = "Task B", status = TodoStatus.IN_PROGRESS, position = 1),
                Todo(id = "3", description = "Task C", status = TodoStatus.COMPLETED, position = 2),
            )
        val prompt = MainSystemPromptComposer.compose(todos, null, toolConfigService)
        assertTrue("TODO summary" in prompt)
        assertTrue("Open: 1" in prompt)
        assertTrue("In Progress: 1" in prompt)
        assertTrue("Done: 1" in prompt)
        assertTrue("Total: 3" in prompt)
        assertTrue("Task A" in prompt)
        assertTrue("Task B" in prompt)
        assertTrue("Task C" in prompt)
    }

    @Test
    fun `prompt includes resume hint`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, "test resume message", toolConfigService)
        assertTrue("Resume Hint" in prompt)
        assertTrue("test resume message" in prompt)
    }

    @Test
    fun `prompt includes resume hint for no pending request`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("no interrupted user request detected" in prompt)
    }

    private class MapSubAgentConfigStore :
        SubAgentConfigStore,
        PreferenceStore {
        private val configs = linkedMapOf<String, SubAgentToolConfig>()
        private val preferences = linkedMapOf<String, String>()

        override fun saveSubAgentConfig(config: SubAgentToolConfig) {
            configs[config.id] = config
        }

        override fun getSubAgentConfig(id: String): SubAgentToolConfig? = configs[id]

        override fun listSubAgentConfigs(): List<SubAgentToolConfig> = configs.values.toList()

        override fun getPreference(key: String): String? = preferences[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            preferences[key] = value
        }
    }
}
