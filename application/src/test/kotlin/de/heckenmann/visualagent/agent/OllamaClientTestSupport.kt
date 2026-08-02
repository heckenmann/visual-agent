package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.config.AppConfigBean
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.ollama.api.OllamaApi

/**
 * Builds a minimal Spring AI chat response for the given model and content.
 *
 * @param model Model name reported in the response metadata.
 * @param content Assistant message content.
 * @return A Spring AI [ChatResponse] suitable for mocking.
 */
fun springResponse(
    model: String,
    content: String,
): ChatResponse =
    ChatResponse(
        listOf(Generation(AssistantMessage(content))),
        ChatResponseMetadata.builder().model(model).build(),
    )

/**
 * Creates an [OllamaClient] wired with a real prompt factory and recovery,
 * backed by the supplied mocked collaborators.
 *
 * @param chatModel Mocked Spring AI chat model.
 * @param ollamaApi Mocked Ollama API client.
 * @param registry Tool registry used by the prompt factory.
 * @return Configured Ollama client instance.
 */
fun createClient(
    chatModel: ChatModel,
    ollamaApi: OllamaApi,
    registry: ToolRegistry,
    appConfig: AppConfigBean = AppConfigBean(createMockPreferenceStore()),
): OllamaClient {
    val promptFactory = OllamaPromptFactory(registry)
    val recovery = OllamaToolRecovery(chatModel, promptFactory)
    return OllamaClient(chatModel, ollamaApi, promptFactory, recovery, registry, appConfig)
}

private fun createMockPreferenceStore(): de.heckenmann.visualagent.knowledge.PreferenceStore =
    object : de.heckenmann.visualagent.knowledge.PreferenceStore {
        private val map = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = map[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            map[key] = value
        }
    }

/**
 * Minimal no-op tool for tests that only need a tool definition in the registry.
 *
 * @param id Tool identifier, also used as the function name.
 */
class FakeTool(
    id: String,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(id),
            name = ToolId(id).toFunctionName(),
            description = "Fake $id",
            inputSchema = """{"type":"object"}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = ToolResult(definition.id.value, true, "ok")
}
