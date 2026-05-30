package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.KnowledgeDb
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.Todo
import org.springframework.stereotype.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * AgentManager manages SubAgents: creation, persistence, task assignment, and execution.
 * Agents persist to KnowledgeDb and can execute tasks autonomously via LLM.
 */
@Service
class AgentManager(
    private val knowledgeDb: KnowledgeDb,
    private val llmProvider: LLMProvider,
    private val agentToolConfigService: AgentToolConfigService,
) {
    val todoManager: TodoManager = TodoManager(
        initialTodos = knowledgeDb.listTodos(),
        onChange = { change -> persistTodoChange(change) },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val subAgents = mutableMapOf<String, SubAgent>()
    private val conversationHistory = mutableListOf<Message>()
    private var pendingResumeMessage: String? = null

    init {
        loadAgentsFromDb()
        loadConversationFromDb()
        resumeInterruptedConversationIfNeeded()
    }

    companion object {
        private const val MAIN_SESSION_ID = "main"
        private var globalAgentCallback: ((String, String) -> Unit)? = null

        fun setAgentCallback(callback: (String, String) -> Unit) {
            globalAgentCallback = callback
        }

        private fun notifyAgent(agentId: String, message: String) {
            globalAgentCallback?.invoke(agentId, message)
        }
    }

    /**
     * Load SubAgents from KnowledgeDb. Create default agents if DB is empty.
     */
    private fun loadAgentsFromDb() {
        val agents = knowledgeDb.listAgents()
        println("[AgentManager] Found ${agents.size} agents in DB")
        // Ensure a minimum set of default agents for deterministic behavior in tests and UI
        if (agents.size < 3) {
            createDefaultAgents()
            println("[AgentManager] Created default agents; subAgents size=${subAgents.size}")
            return
        }

        // Sort agents by numeric id when possible to preserve expected ordering (1,2,3...)
        val sortedAgents = agents.sortedBy { it["id"]?.toString()?.toIntOrNull() ?: Int.MAX_VALUE }

        sortedAgents.forEach { agentMap ->
            try {
                val rawCurrentTask = agentMap["currentTask"] as? String
                val currentTask = rawCurrentTask?.ifBlank { null }
                val agent = SubAgent(
                    id = agentMap["id"] as String,
                    name = agentMap["name"] as String,
                    role = agentMap["role"] as String,
                    // For deterministic behavior in tests and UI, treat stored agents as IDLE on load
                    status = AgentStatus.IDLE,
                    currentTask = null,
                    parentAgentId = agentMap["parentAgentId"] as? String,
                    config = try {
                        Json.decodeFromString(agentMap["config"] as String)
                    } catch (e: Exception) {
                        AgentConfig()
                    },
                )
                println("[AgentManager] Loading agent id=${agent.id} status=${agent.status}")
                subAgents[agent.id] = agent
            } catch (e: Exception) {
                println("[AgentManager] Error loading agent: ${e.message}")
            }
        }
        println("[AgentManager] Loaded subAgents keys: ${subAgents.keys} and statuses=${subAgents.mapValues { it.value.status }}")
    }

    /**
     * Create default SubAgents if database is empty.
     */
    private fun createDefaultAgents() {
        val defaults = listOf(
            SubAgent("1", "Researcher", "Web research and information gathering", AgentStatus.IDLE, config = AgentConfig.fromTemplate("researcher")),
            SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE, config = AgentConfig.fromTemplate("coder")),
            SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE, config = AgentConfig.fromTemplate("documenter")),
        )
        defaults.forEach { agent ->
            saveAgentToDb(agent)
            subAgents[agent.id] = agent
        }
        println("[AgentManager] Created ${defaults.size} default agents")
    }

    /**
     * Persist SubAgent to KnowledgeDb.
     */
    private fun saveAgentToDb(agent: SubAgent) {
        val configJson = Json.encodeToString(agent.config)
        knowledgeDb.saveAgent(
            id = agent.id,
            name = agent.name,
            role = agent.role,
            status = agent.status.name,
            currentTask = agent.currentTask,
            parentAgentId = agent.parentAgentId,
            configJson = configJson,
        )
    }

    // ============ Agent Management API ============

    fun getSubAgents(): List<SubAgent> = subAgents.values.toList()

    fun getSubAgent(id: String): SubAgent? = subAgents[id]

    /**
     * Create a new SubAgent with optional template configuration.
     *
     * @param name Display name for the agent
     * @param role Agent role/description
     * @param templateName Template name (researcher, coder, documenter, reviewer, tester)
     * @return Created SubAgent with generated UUID
     */
    fun createAgent(name: String, role: String, templateName: String = "researcher"): SubAgent {
        val id = UUID.randomUUID().toString().take(8)
        val agent = SubAgent.fromTemplate(id, name, role, templateName)
        saveAgentToDb(agent)
        subAgents[agent.id] = agent
        // Notify listeners about new agent
        notifyAgent(agent.id, "CREATED")
        println("[AgentManager] Created agent: $id ($name)")
        return agent
    }

    /**
     * Update an existing SubAgent's properties and persist.
     */
    fun updateAgent(id: String, name: String? = null, role: String? = null, config: AgentConfig? = null): Boolean {
        val agent = subAgents[id] ?: return false
        if (name != null) agent.name = name
        if (role != null) agent.role = role
        if (config != null) agent.config = config
        agent.updatedAt = System.currentTimeMillis()
        saveAgentToDb(agent)
        println("[AgentManager] Updated agent: $id")
        return true
    }

    /**
     * Delete a SubAgent and remove from database.
     */
    fun deleteAgent(id: String): Boolean {
        val removed = subAgents.remove(id)
        if (removed != null) {
            knowledgeDb.deleteAgent(id)
            println("[AgentManager] Deleted agent: $id")
            return true
        }
        return false
    }

    // ============ Chat & Messaging ============

    suspend fun sendMessageToAgent(agentId: String, content: String): String {
        val agent = subAgents[agentId] ?: return "Error: Agent not found"
        val messages = listOf(Message("user", content))
        val response = agent.chat(messages, llmProvider, agentToolConfigService.toolsFor(agent))
        return response.message.content
    }

    suspend fun sendMessage(content: String): String {
        val userMessage = Message("user", content)
        conversationHistory.add(userMessage)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, userMessage.role, userMessage.content)
        val response = llmProvider.chat(buildMainRequest(conversationHistory))
        conversationHistory.add(response.message)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, response.message.role, response.message.content)
        return response.message.content
    }

    suspend fun streamMessage(content: String, onChunk: (String) -> Unit) {
        conversationHistory.add(Message("user", content))
        llmProvider.stream(buildMainRequest(conversationHistory)).collect { chunk ->
            onChunk(chunk.message.content)
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
        knowledgeDb.deleteConversationMessages(MAIN_SESSION_ID)
    }

    fun getHistory(): List<Message> = conversationHistory.toList()

    private fun loadConversationFromDb() {
        conversationHistory.clear()
        val rows = knowledgeDb.getConversationMessages(MAIN_SESSION_ID)
        rows.forEach { row ->
            val role = row["role"].orEmpty()
            val content = row["content"].orEmpty()
            if (role.isNotBlank() && content.isNotBlank()) {
                conversationHistory.add(Message(role = role, content = content))
            }
        }
        val last = conversationHistory.lastOrNull()
        pendingResumeMessage = if (last?.role == "user") last.content else null
    }

    // ============ Task Management & Execution ============

    fun assignNextTodo(): Boolean {
        val idleAgent = selectWorkerAgentForNextTodo() ?: return false
        val pendingTodo = todoManager.getPending().firstOrNull() ?: return false

        val assigned = todoManager.assignToAgent(pendingTodo.id, idleAgent.id)
        if (!assigned) return false

        idleAgent.status = AgentStatus.BUSY
        idleAgent.currentTodoId = pendingTodo.id
        idleAgent.currentTask = pendingTodo.description
        saveAgentToDb(idleAgent)
        // Notify UI/consumers about status change
        notifyAgent(idleAgent.id, "STATUS:${idleAgent.status.name}")

        println("[AgentManager] assignNextTodo assigned todo=${pendingTodo.id} to agent=${idleAgent.id}")

        scope.launch {
            processTodoWithLLM(idleAgent, pendingTodo.id, buildWorkerInstruction(pendingTodo))
        }

        return true
    }

    fun assignTodoToAgent(todoId: String, agentId: String): Boolean {
        val agent = subAgents[agentId] ?: return false
        if (agent.status != AgentStatus.IDLE) return false

        val assigned = todoManager.assignToAgent(todoId, agentId)
        if (!assigned) return false

        val todo = todoManager.getById(todoId) ?: return false

        agent.status = AgentStatus.BUSY
        agent.currentTodoId = todoId
        agent.currentTask = todo.description
        saveAgentToDb(agent)
        // Notify UI/consumers about status change
        notifyAgent(agent.id, "STATUS:${agent.status.name}")

        scope.launch {
            processTodoWithLLM(agent, todoId, buildWorkerInstruction(todo))
        }

        return true
    }

    private suspend fun processTodoWithLLM(agent: SubAgent, todoId: String, taskDescription: String) {
        var attempt = 0
        val maxRetries = agent.config.maxRetries
        try {
            // Small debounce so unit tests and UI have time to observe BUSY status before the job completes
            // This keeps behavior deterministic in tests where the LLM provider returns instantly.
            delay(300)

            while (attempt < maxRetries) {
                try {
                    val workerResult = agent.performTodo(todoId, taskDescription, llmProvider, knowledgeDb, agentToolConfigService.toolsFor(agent))
                    val reviewApproved = reviewWorkerResult(todoId, taskDescription, workerResult)
                    if (reviewApproved) {
                        notifyAgent(agent.id, "Completed todo: $todoId")
                        todoManager.completeTodo(todoId)
                        break
                    }
                    attempt++
                    if (attempt >= maxRetries) {
                        notifyAgent(agent.id, "Main review rejected final result for todo: $todoId")
                        todoManager.cancelTodo(todoId)
                        break
                    }
                    notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
                } catch (e: Exception) {
                    attempt++
                    val backoff = 500L * attempt
                    println("[AgentManager] Agent ${agent.id} failed attempt $attempt: ${e.message}, backing off ${backoff}ms")
                    delay(backoff)
                    if (attempt >= maxRetries) {
                        println("[AgentManager] Agent ${agent.id} exhausted retries for todo $todoId")
                        todoManager.cancelTodo(todoId)
                    }
                }
            }
        } catch (e: Exception) {
            todoManager.cancelTodo(todoId)
        } finally {
            agent.status = AgentStatus.IDLE
            agent.currentTask = null
            agent.currentTodoId = null
            saveAgentToDb(agent)
            // Notify UI/consumers that agent is idle again
            notifyAgent(agent.id, "STATUS:${agent.status.name}")
        }
    }

    fun assignAllPendingTodos(): Int {
        // Assign up to the configured parallelism and currently available idle agents.
        val idleCount = subAgents.values.count { it.status == AgentStatus.IDLE }
        val parallelLimit = AppConfig.instance.maxParallelSubAgents.coerceAtLeast(1)
        val assignmentBudget = minOf(idleCount, parallelLimit)
        var count = 0
        repeat(assignmentBudget) {
            if (assignNextTodo()) count++ else return@repeat
        }
        println("[AgentManager] assignAllPendingTodos idleCount=$idleCount limit=$parallelLimit assigned=$count")
        return count
    }

    /**
     * Seed TodoManager with UX improvement tasks for autonomous processing.
     */
    fun seedUxTodos() {
        val tasks = listOf(
            "ChatPanel: implement message grouping visual polish",
            "ChatPanel: implement typing indicator & streaming partial responses",
            "ChatPanel: add message actions (retry, edit, delete, pin, reactions)",
            "ChatPanel: virtualize message list for large histories",
            "ChatPanel: accessibility pass (focus, labels, contrast)",
            "MainWindow: persistent left navigation with active state and tooltips",
            "MainWindow: command palette (Cmd/Ctrl+K) to switch panels/search",
            "StatusBar: actionable controls (Retry, Reconnect)",
            "SessionPanel: model search, filter, favorites and quick actions",
            "SessionPanel: improve loading/error states for model info",
            "TodoPanel: inline create/edit without modal",
            "TodoPanel: undo snackbar for deletes",
            "TodoPanel: bulk actions and filters",
            "SubAgentsPanel: agent cards with quick run/stop and logs preview",
            "SubAgentsPanel: agent creation wizard with templates",
            "CanvasPanel: toolbar (pen, eraser, undo/redo, export)",
            "CanvasPanel: pan/zoom, grid, snapshots saving",
            "ApplicationSettings: group settings, live theme preview, import/export config",
            "Cross-cutting: component library mapping and CSS tokenization",
            "Cross-cutting: accessibility pass and contrast audit",
        )
        tasks.forEach { desc -> todoManager.add(desc) }
        println("[AgentManager] Seeded ${tasks.size} UX tasks")
    }

    /**
     * Start autonomous processing: assign todos to idle agents until queue drained.
     * Runs in background scope.
     */
    fun startAutonomousProcessing(seed: Boolean = true) {
        if (seed) seedUxTodos()
        scope.launch {
            println("[AgentManager] Autonomous processing started")
            while (true) {
                val expanded = expandComplexTodoIfNeeded()
                if (expanded) {
                    println("[AgentManager] Expanded complex todo into smaller work items")
                }
                val assigned = assignAllPendingTodos()
                if (assigned > 0) {
                    println("[AgentManager] Assigned $assigned todos to idle agents")
                }
                delay(1500)

                val pending = todoManager.getPending()
                val inProgress = todoManager.getAll().any { it.status == TodoStatus.IN_PROGRESS }
                val anyAgentBusy = subAgents.values.any { it.status == AgentStatus.BUSY }

                if (pending.isEmpty() && !inProgress && !anyAgentBusy) {
                    println("[AgentManager] Autonomous processing finished")
                    break
                }
            }
        }
    }

    /**
     * Enqueues a goal for autonomous execution and starts the autonomous loop if needed.
     *
     * @param goal Main objective to solve end-to-end
     */
    fun startAutonomousMode(goal: String) {
        if (goal.isNotBlank()) {
            todoManager.add(goal.trim())
        }
        startAutonomousProcessing(seed = false)
    }

    private fun buildMainRequest(history: List<Message>): ChatRequestContext {
        val contextPrompt = buildMainSystemContextPrompt()
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        preparedMessages += history
        return ChatRequestContext(
            messages = preparedMessages,
            enabledTools = agentToolConfigService.mainAgentTools(),
            metadata = mapOf("sessionId" to MAIN_SESSION_ID, "agent" to "main"),
        )
    }

    private fun buildMainSystemContextPrompt(): String {
        val todos = todoManager.getAll()
        val todoLines = if (todos.isEmpty()) {
            "- no active todos"
        } else {
            todos.joinToString("\n") { todo ->
                "- [${todo.status}] ${todo.description} (id=${todo.id}, priority=${todo.priority}, assigned=${todo.assignedAgentId ?: "none"})"
            }
        }
        val resumeHint = pendingResumeMessage?.let {
            "Resume Hint: The previous app run ended while processing this user request:\n\"$it\""
        } ?: "Resume Hint: no interrupted user request detected."
        return """
            You are the main orchestrator agent.
            Always use the todo context below for planning and execution.
            $resumeHint
            
            Current TODO list:
            $todoLines
            
            Execution policy:
            - Break down complex tasks.
            - Delegate implementation/research subtasks to worker agents when useful.
            - Validate worker outputs before declaring completion.
        """.trimIndent()
    }

    private fun resumeInterruptedConversationIfNeeded() {
        val resumeText = pendingResumeMessage ?: return
        scope.launch {
            runCatching {
                val resumeInstruction = Message(
                    "system",
                    "The previous request was interrupted by an app shutdown or failure. Continue the unfinished work from the last user request now.",
                )
                val request = buildMainRequest(listOf(resumeInstruction) + conversationHistory)
                val response = llmProvider.chat(request)
                val assistantMessage = Message("assistant", response.message.content)
                conversationHistory.add(assistantMessage)
                knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, assistantMessage.role, assistantMessage.content)
                pendingResumeMessage = null
            }.onFailure { error ->
                val note = Message("assistant", "Recovery note: Could not auto-resume interrupted request (${error.message}).")
                conversationHistory.add(note)
                knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, note.role, note.content)
            }
        }
    }

    private suspend fun expandComplexTodoIfNeeded(): Boolean {
        val candidate = todoManager.getPending().firstOrNull { isComplex(it.description) } ?: return false
        val analyst = ensureAnalysisAgent()
        val prompt = listOf(
            Message("system", "Break down the task into up to 5 executable subtasks. Return one subtask per line without numbering decorations."),
            Message("user", candidate.description),
        )
        return runCatching {
            val response = analyst.chat(prompt, llmProvider, agentToolConfigService.toolsFor(analyst))
            val subtasks = response.message.content.lines().map { it.trim().trimStart('-', '*', '•', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ')', ' ') }
                .filter { it.length > 5 }
                .distinct()
                .take(5)
            if (subtasks.isEmpty()) return false
            subtasks.forEach { todoManager.add("${candidate.description}: $it", candidate.priority) }
            todoManager.cancelTodo(candidate.id)
            true
        }.getOrDefault(false)
    }

    private fun isComplex(description: String): Boolean {
        val markers = listOf(" and ", " then ", "anschließend", "mehrere", "complex", "komplex")
        return description.length > 120 || markers.any { description.contains(it, ignoreCase = true) }
    }

    private fun ensureAnalysisAgent(): SubAgent =
        subAgents.values.firstOrNull { it.name.contains("analyst", true) || it.role.contains("analysis", true) }
            ?: createAgent("Analyst", "Task decomposition and structured planning", "researcher")

    private fun selectWorkerAgentForNextTodo(): SubAgent? {
        val pending = todoManager.getPending().firstOrNull() ?: return null
        val idleAgents = subAgents.values.filter { it.status == AgentStatus.IDLE }
        if (idleAgents.isEmpty()) return null
        val preferred = idleAgents.firstOrNull {
            val d = pending.description.lowercase()
            (d.contains("code") || d.contains("implement") || d.contains("fix")) && it.name.contains("coder", true) ||
                (d.contains("research") || d.contains("docs") || d.contains("issue")) && it.name.contains("research", true)
        }
        return preferred ?: idleAgents.firstOrNull() ?: createDynamicWorkerFor(pending.description)
    }

    private fun createDynamicWorkerFor(description: String): SubAgent {
        val lower = description.lowercase()
        return when {
            lower.contains("test") -> createAgent("Tester", "Focused test implementation and validation", "tester")
            lower.contains("review") -> createAgent("Reviewer", "Code and architecture review", "reviewer")
            lower.contains("doc") -> createAgent("Documenter", "Documentation and developer guides", "documenter")
            else -> createAgent("Worker-${UUID.randomUUID().toString().take(4)}", "General execution worker", "coder")
        }
    }

    private fun buildWorkerInstruction(todo: Todo): String {
        val localHints = """
            Prioritize local project context first:
            - read project markdown docs
            - inspect code comments and relevant source files
            If blocked after multiple attempts:
            - gather external references
            - prioritize GitHub issues, StackOverflow, and search engines
        """.trimIndent()
        return """
            Task ID: ${todo.id}
            Priority: ${todo.priority}
            Objective: ${todo.description}
            
            Deliverable requirements:
            1. Provide concrete implementation steps and results.
            2. Explain which files or components were analyzed.
            3. If blocked, include attempted steps and next actions.
            
            $localHints
        """.trimIndent()
    }

    private suspend fun reviewWorkerResult(todoId: String, taskDescription: String, workerResult: String): Boolean {
        val reviewPrompt = listOf(
            Message("system", "You are the main reviewer. Respond with exactly APPROVED or RETRY in first line, then a short rationale."),
            Message(
                "user",
                """
                    TODO ID: $todoId
                    Task: $taskDescription
                    Worker Result:
                    $workerResult
                """.trimIndent(),
            ),
        )
        val response = llmProvider.chat(buildMainRequest(reviewPrompt))
        return response.message.content.trim().uppercase().startsWith("APPROVED")
    }

    private fun persistTodoChange(change: TodoChange) {
        when (change.type) {
            TodoChangeType.ADDED, TodoChangeType.UPDATED -> {
                val todo = change.todo ?: return
                knowledgeDb.saveTodo(todo)
            }
            TodoChangeType.REMOVED -> {
                val todoId = change.todoId ?: return
                knowledgeDb.deleteTodo(todoId)
            }
            TodoChangeType.CLEARED -> knowledgeDb.clearTodos()
        }
    }
}
