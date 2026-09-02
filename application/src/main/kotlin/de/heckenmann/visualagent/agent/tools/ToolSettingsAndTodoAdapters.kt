package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.startAllTodos
import de.heckenmann.visualagent.agent.startTodo
import de.heckenmann.visualagent.agent.stopAllTodos
import de.heckenmann.visualagent.agent.stopTodo
import de.heckenmann.visualagent.agent.tools.api.TodoAssignmentMode
import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.TodoUpdateRequest
import de.heckenmann.visualagent.agent.tools.api.ToolSettings
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsUpdate
import de.heckenmann.visualagent.agent.tools.api.ToolTodo
import de.heckenmann.visualagent.agent.tools.api.ToolTodoCreation
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoAssignmentChange
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import org.springframework.stereotype.Component

/** Application adapter for safe settings consumed by tools. */
@Component
class ToolSettingsPortAdapter(
    private val providerCatalog: ProviderCatalogService,
    private val appConfig: AppConfigBean,
) : ToolSettingsPort {
    override fun read(): ToolSettings {
        val provider = providerCatalog.activeProviderId()
        return ToolSettings(
            fontSize = appConfig.fontSize,
            provider = provider,
            model = providerCatalog.activeModelId(),
            openAiBaseUrl = appConfig.openAiBaseUrl,
            openAiApiKeyConfigured = appConfig.openAiApiKey.isNotBlank(),
            timeoutSeconds = appConfig.timeoutSeconds,
            uiScalePercent = appConfig.uiScalePercent,
        )
    }

    override fun update(update: ToolSettingsUpdate): ToolSettings {
        update.fontSize?.let { appConfig.fontSize = it }
        update.provider?.let(providerCatalog::setActiveProvider)
        update.model?.let { model ->
            val provider = providerCatalog.getProvider(providerCatalog.activeProviderId())
            if (provider != null) providerCatalog.saveProvider(provider.copy(defaultModel = model))
        }
        update.openAiBaseUrl?.let { appConfig.openAiBaseUrl = it }
        update.uiScalePercent?.let { percent ->
            appConfig.uiScalePercent = percent.takeIf { it != 0 }?.coerceIn(50, 200)
        }
        appConfig.save()
        return read()
    }
}

/** Application adapter for todo persistence and lifecycle operations consumed by tools. */
class TodoToolPortAdapter(
    private val todoStore: TodoStore,
    private val memoryStore: MemoryStore,
    private val todoManagerProvider: () -> TodoManager,
    private val agentManagerProvider: () -> AgentManager,
) : TodoToolPort {
    private val todoManager: TodoManager get() = todoManagerProvider()

    override fun list(): List<ToolTodo> =
        todoStore.listTodos().map {
            ToolTodo(it.id, it.description, it.status.name, it.position, it.assignedAgentId)
        }

    override fun agentExists(agentId: String): Boolean = agentManagerProvider().getSubAgent(agentId) != null

    override fun add(
        description: String,
        assignedAgentId: String,
    ): String = todoManager.add(description, assignedAgentId).id

    override fun addIfAbsent(
        description: String,
        assignedAgentId: String,
    ): ToolTodoCreation =
        todoManager.addIfAbsent(description, assignedAgentId).let { creation ->
            ToolTodoCreation(
                ToolTodo(
                    creation.todo.id,
                    creation.todo.description,
                    creation.todo.status.name,
                    creation.todo.position,
                    creation.todo.assignedAgentId,
                ),
                creation.created,
            )
        }

    override fun update(request: TodoUpdateRequest): Boolean {
        val assignment =
            when (request.assignmentMode) {
                TodoAssignmentMode.UNCHANGED -> TodoAssignmentChange.Unchanged
                TodoAssignmentMode.CLEAR -> TodoAssignmentChange.Clear
                TodoAssignmentMode.SET -> TodoAssignmentChange.Set(request.assignedAgentId ?: return false)
            }
        val status = request.status?.let { runCatching { TodoStatus.valueOf(it) }.getOrNull() ?: return false }
        return todoManager.update(
            TodoUpdateCommand(
                id = request.id,
                description = request.description,
                assignment = assignment,
                status = status,
            ),
        )
    }

    override fun setStatus(
        id: String,
        status: String,
    ): Boolean =
        when (TodoStatus.valueOf(status)) {
            TodoStatus.COMPLETED -> todoManager.completeTodo(id)
            TodoStatus.CANCELLED -> todoManager.cancelTodo(id)
            else -> todoManager.updateStatus(id, TodoStatus.valueOf(status))
        }

    override fun start(id: String): Boolean = agentManagerProvider().startTodo(id)

    override fun startAll(): Int = agentManagerProvider().startAllTodos()

    override fun stop(id: String): Boolean = agentManagerProvider().stopTodo(id)

    override fun stopAll(): Int = agentManagerProvider().stopAllTodos()

    override fun remove(id: String): Boolean = todoManager.remove(id)

    override fun moveToPosition(
        id: String,
        position: Int,
    ): Boolean = todoManager.moveToPosition(id, position)

    override fun result(id: String): String? = memoryStore.searchMemories("todo:$id", limit = 1).firstOrNull()?.content
}
