package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AgentManager(
    private val knowledgeDb: KnowledgeDb,
    private val ollamaClient: OllamaClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val subAgents = mutableMapOf<String, SubAgent>()
    private val conversationHistory = mutableListOf<Message>()

    init {
        loadAgentsFromDb()
    }

    private fun loadAgentsFromDb() {
        subAgents["1"] = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE)
        subAgents["2"] = SubAgent("2", "Coder", "Code implementation", AgentStatus.IDLE)
        subAgents["3"] = SubAgent("3", "Documenter", "Documentation", AgentStatus.IDLE)
    }

    fun getSubAgents(): List<SubAgent> = subAgents.values.toList()

    fun getSubAgent(id: String): SubAgent? = subAgents[id]

    suspend fun sendMessage(content: String): String {
        conversationHistory.add(Message("user", content))

        val response = ollamaClient.chat(conversationHistory)

        conversationHistory.add(response.message)

        return response.message.content
    }

    suspend fun streamMessage(content: String, onChunk: (String) -> Unit) {
        conversationHistory.add(Message("user", content))

        ollamaClient.stream(conversationHistory).collect { chunk ->
            onChunk(chunk.message.content)
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun getHistory(): List<Message> = conversationHistory.toList()
}
