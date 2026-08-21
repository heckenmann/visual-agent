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
        assertTrue("Repository file operations" in prompt)
        assertTrue("Terminal commands" in prompt)
        assertTrue("Browser or search" in prompt)
        assertTrue("Canvas operations" in prompt)
        assertTrue("Research or analysis" in prompt)
        assertTrue("History search" in prompt)
        assertTrue("workspace:file" in prompt)
        assertTrue("You may perform these workspace actions yourself or delegate them" in prompt)
        assertTrue("Never include a native write-permission preflight" in prompt)
        assertTrue("A `workspace:file` action is the authoritative capability check" in prompt)
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
    fun `prompt contains reliable image embedding guidance`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("Embedding Images in the Conversation" in prompt)
        assertTrue("![descriptive alt text](source)" in prompt)
        assertTrue("workspace:relative/path/image.png" in prompt)
        assertTrue("server-file:relative/path/image.png" in prompt)
        assertTrue("client-file:/absolute/path" in prompt)
        assertTrue("data:image/png;base64" in prompt)
        assertTrue("Do not claim that an image is displayed" in prompt)
    }

    @Test
    fun `prompt explains explicit todo execution control`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("automatically set to PENDING" in prompt)
        assertTrue("execution is stopped" in prompt)
        assertTrue("start-all" in prompt)
        assertTrue("explicitly asks" in prompt)
    }

    @Test
    fun `prompt explains auto-notification on todo completion`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)
        assertTrue("completes or cancels" in prompt)
        assertTrue("notification appears" in prompt)
        assertTrue("automatically prompted" in prompt)
    }

    @Test
    fun `prompt requires a fresh todo status check before every add`() {
        val prompt = MainSystemPromptComposer.compose(emptyTodos, null, toolConfigService)

        assertTrue("Before every" in prompt && "`{\"action\":\"list\"}`" in prompt)
        assertTrue("never add another one" in prompt)
        assertTrue("PENDING, IN_PROGRESS, COMPLETED, and CANCELLED" in prompt)
        assertTrue("Do not recreate or restart" in prompt)
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
