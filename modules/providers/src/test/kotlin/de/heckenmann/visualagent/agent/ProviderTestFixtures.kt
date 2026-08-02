package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition as SpringToolDefinition

internal class TestProviderRuntimeConfig(
    override var llmProvider: String = "ollama",
    override var ollamaLocalUrl: String = "http://localhost:11434",
    override var ollamaModel: String = "llava",
    override var ollamaApiKey: String = "",
    override var openAiApiKey: String = "",
    override var openAiBaseUrl: String = "https://api.openai.com",
    override var openAiModel: String = "gpt-4o-mini",
    override var timeoutSeconds: Int = 120,
) : ProviderRuntimeConfig {
    override fun normalizedProvider(): String = if (llmProvider.equals("openai", ignoreCase = true)) "openai" else "ollama"
}

internal class TestPreferenceStore : ProviderPreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

internal interface TestVisualAgentTool {
    val definition: ToolDefinition

    fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult
}

internal class TestToolRegistry(
    private val tools: List<TestVisualAgentTool> = emptyList(),
    @Suppress("UNUSED_PARAMETER") vararg ignored: Any?,
) : ProviderToolCallbacks {
    override fun functionCallbacks(
        enabledTools: Set<ToolId>,
        context: Map<String, Any>,
    ): List<ToolCallback> =
        tools
            .filter { it.definition.id in enabledTools }
            .sortedBy { it.definition.name }
            .map { tool ->
                object : ToolCallback {
                    override fun getToolDefinition(): SpringToolDefinition =
                        SpringToolDefinition
                            .builder()
                            .name(tool.definition.name)
                            .description(tool.definition.description)
                            .inputSchema(tool.definition.inputSchema)
                            .build()

                    override fun call(functionInput: String): String = tool.execute(functionInput, context).content
                }
            }
}

internal fun ToolId.toTestFunctionName(): String = value.replace(Regex("[^A-Za-z0-9_]"), "_")
