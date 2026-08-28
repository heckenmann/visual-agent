package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsUpdate

/** Tool that exposes safe application/session settings to the model. */
@AgentTool
class SettingsTool(
    private val settings: ToolSettingsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("ui"),
            name = ToolId("ui").toFunctionName(),
            description =
                "Read or update Visual Agent UI settings. Actions: get, set. " +
                    "Input: {\"action\":\"get|set\",\"fontSize\":14,\"uiScalePercent\":125,\"provider\":\"ollama\"," +
                    "\"model\":\"llama3\",\"streamingEnabled\":true,\"thinkingEnabled\":false}. " +
                    "Font size range: 10-24. API keys are reported as configured/not configured only.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        when (input.string("action") ?: "get") {
            "set" -> {
                settings.update(
                    ToolSettingsUpdate(
                        fontSize = input.int("fontSize")?.coerceIn(10, 24),
                        provider = input.string("provider"),
                        model = input.string("model"),
                        openAiBaseUrl = input.string("openAiBaseUrl"),
                        streamingEnabled = input.boolean("streamingEnabled"),
                        thinkingEnabled = input.boolean("thinkingEnabled"),
                        uiScalePercent =
                            input.int("uiScalePercent")?.let { percent ->
                                percent.takeIf { it == 0 } ?: percent.coerceIn(50, 200)
                            },
                    ),
                )
            }
            "get" -> Unit
            else -> return failure("ui", "Unsupported ui action")
        }
        val current = settings.read()
        return success(
            "ui",
            """
            Current UI Settings:
              Font size: ${current.fontSize}px
              Provider: ${current.provider}
              Model: ${current.model}
              OpenAI Base URL: ${current.openAiBaseUrl}
              OpenAI API key configured: ${current.openAiApiKeyConfigured}
              Streaming: ${current.streamingEnabled}
              Thinking: ${current.thinkingEnabled}
              UI scale: ${current.uiScalePercent?.let { "$it%" } ?: "Automatic"}
            Font size range: 10-24. UI scale range: 50-200; use 0 for automatic scaling.
            """.trimIndent(),
        )
    }
}

/** Tool that returns the workspace root used for file and terminal operations. */
@AgentTool
class PwdTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("pwd"),
            name = ToolId("pwd").toFunctionName(),
            description =
                "Return the current Visual Agent workspace directory. " +
                    "No input parameters required. " +
                    "Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = success("pwd", workspaceRoot().toString())
}

/** Tool that summarizes request metadata, workspace state, and provider selection. */
@AgentTool
class ContextTool(
    private val settings: ToolSettingsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("context"),
            name = ToolId("context").toFunctionName(),
            description =
                "Return current model, session, agent, workspace, and enabled tool context. " +
                    "No input parameters required. " +
                    "Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        success(
            "context",
            buildString {
                appendLine("Workspace: ${workspaceRoot()}")
                val current = settings.read()
                appendLine("Provider: ${current.provider}")
                appendLine("Model: ${current.model}")
                appendLine("OpenAI Base URL: ${current.openAiBaseUrl}")
                appendLine("OpenAI API key configured: ${current.openAiApiKeyConfigured}")
                context.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }.trim(),
        )
}
