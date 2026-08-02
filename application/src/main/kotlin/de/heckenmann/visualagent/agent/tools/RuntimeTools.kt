package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Tool that runs bounded, non-interactive shell commands in the workspace.
 *
 * Use cases: UC-0000021.
 */
@Component
class TerminalTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("terminal"),
            name = ToolId("terminal").toFunctionName(),
            description =
                "Run a non-interactive shell command in the workspace directory. " +
                    "Input: {\"command\":\"ls -la\",\"timeoutSeconds\":10}. " +
                    "timeoutSeconds range: 1-30. Returns stdout + stderr combined. " +
                    "Exit code is included in the result. Use for building, testing, git, and file operations.",
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
            ProcessBuilder(shellCommand(command))
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

    private fun shellCommand(command: String): List<String> {
        val shell =
            listOf("zsh", "bash", "sh")
                .firstOrNull(::isExecutableOnPath) ?: "sh"
        return listOf(shell, "-lc", command)
    }

    private fun isExecutableOnPath(command: String): Boolean {
        val path = System.getenv("PATH").orEmpty()
        return path
            .split(java.io.File.pathSeparator)
            .asSequence()
            .map { Path.of(it, command) }
            .any { Files.isExecutable(it) }
    }
}

/**
 * Placeholder tool for browser automation when no browser backend is configured.
 *
 * Use cases: UC-0000044.
 */
@Component
class BrowserTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("browser"),
            name = ToolId("browser").toFunctionName(),
            description =
                "Browser automation interface. " +
                    "Currently not available — no browser backend is configured. " +
                    "Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = failure("browser", "Browser backend is not configured in this application runtime")
}

/**
 * Placeholder tool for web/search access when no search backend is configured.
 *
 * Use cases: UC-0000044.
 */
@Component
class SearchTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("search"),
            name = ToolId("search").toFunctionName(),
            description =
                "Search interface. " +
                    "Currently not available — no search backend is configured. " +
                    "Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = failure("search", "Search backend is not configured in this application runtime")
}

/**
 * Tool that pauses execution for a bounded duration.
 *
 * Use cases: UC-0000043.
 */
@Component
class SleepTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("sleep"),
            name = ToolId("sleep").toFunctionName(),
            description =
                "Wait for a specified duration. " +
                    "Input: {\"seconds\":2}. " +
                    "seconds range: 0-300. Use for waiting between operations or polling.",
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
