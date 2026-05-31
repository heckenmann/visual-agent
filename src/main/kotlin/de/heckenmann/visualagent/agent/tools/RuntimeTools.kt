package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Tool for running bounded shell commands in the workspace.
 */
@Component
class TerminalTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("terminal"),
            name = ToolId("terminal").toFunctionName(),
            description = "Run a non-interactive shell command in the workspace. Input: {\"command\":\"pwd\",\"timeoutSeconds\":10}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val command = input.requiredString("command")
        val timeout = (input.int("timeoutSeconds") ?: 10).coerceIn(1, 30)
        val process =
            ProcessBuilder("zsh", "-lc", command)
                .directory(workspaceRoot().toFile())
                .redirectErrorStream(true)
                .start()
        val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return failure("terminal", "Command timed out after ${timeout}s")
        }
        val output =
            process.inputStream
                .readBytes()
                .toString(StandardCharsets.UTF_8)
                .take(8000)
        return ToolResult(
            "terminal",
            process.exitValue() == 0,
            output,
            if (process.exitValue() == 0) null else "Exit ${process.exitValue()}",
        )
    }
}

/**
 * Placeholder browser tool until an in-app browser backend is wired to the application.
 */
@Component
class BrowserTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("browser"),
            name = ToolId("browser").toFunctionName(),
            description = "Browser automation interface. Currently reports unavailable when no backend is configured.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = failure("browser", "Browser backend is not configured in this application runtime")
}

/**
 * Placeholder search tool until a search backend is wired to the application.
 */
@Component
class SearchTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("search"),
            name = ToolId("search").toFunctionName(),
            description = "Search interface. Currently reports unavailable when no backend is configured.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = failure("search", "Search backend is not configured in this application runtime")
}
