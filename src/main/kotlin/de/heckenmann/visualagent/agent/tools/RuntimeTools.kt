package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Represents TerminalTool.
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
 * Represents BrowserTool.
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
 * Represents SearchTool.
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

/**
 * Represents SleepTool.
 */
@Component
class SleepTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("sleep"),
            name = ToolId("sleep").toFunctionName(),
            description = "Wait for a specified duration. Input: {\"seconds\":2}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val seconds = (input.int("seconds") ?: 1).coerceIn(0, 300)
        runCatching { TimeUnit.SECONDS.sleep(seconds.toLong()) }
            .onFailure { error -> return failure("sleep", error.message ?: "Interrupted") }
        return success("sleep", "slept ${seconds}s")
    }
}
