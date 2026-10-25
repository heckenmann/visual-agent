package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Tool that runs bounded, non-interactive shell commands in the workspace.
 *
 * Use cases: UC-0000021.
 */
@AgentTool
class TerminalTool : LiveVisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("terminal"),
            name = ToolId("terminal").toFunctionName(),
            description =
                "Run a non-interactive shell command in the workspace directory. " +
                    "Input: {\"command\":\"ls -la\"}. Returns stdout + stderr combined. " +
                    "Exit code is included in the result. Use for building, testing, git, and file operations.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = executeCommand(inputJson, context, null)

    override fun executeLiveReactive(
        inputJson: String,
        context: Map<String, Any>,
        reporter: ToolLiveReporter,
    ): reactor.core.publisher.Mono<ToolResult> =
        reactor.core.publisher.Mono
            .fromCallable { trackedToolWork(context) { executeCommand(inputJson, context, reporter) } }

    private fun executeCommand(
        inputJson: String,
        context: Map<String, Any>,
        reporter: ToolLiveReporter?,
    ): ToolResult {
        val input = parseObject(inputJson)
        val command = input.requiredString("command")
        val process =
            ProcessBuilder(shellCommand(command))
                .directory(terminalWorkspaceRoot().toFile())
                .redirectErrorStream(true)
                .start()
        val cancellationRegistration =
            (context["toolCancellationToken"] as? ToolCancellationToken)?.onCancelled { destroyProcessTree(process) }
        val output = StringBuilder()
        val recent = ArrayDeque<String>()
        try {
            reporter?.report(
                de.heckenmann.visualagent.model
                    .ToolLiveState("", 0, "Running command"),
            )
            process.inputStream.reader(StandardCharsets.UTF_8).use { reader ->
                val chunk = CharArray(512)
                var count = reader.read(chunk)
                while (count >= 0) {
                    if (output.length < 8000) output.append(chunk, 0, count.coerceAtMost(8000 - output.length))
                    recent.addLast(String(chunk, 0, count))
                    if (recent.size > 40) recent.removeFirst()
                    reporter?.report(
                        de.heckenmann.visualagent.model
                            .ToolLiveState("", 0, "Running command", logs = recent.toList()),
                    )
                    count = reader.read(chunk)
                }
            }
            process.waitFor()
        } catch (_: InterruptedException) {
            destroyProcessTree(process)
            Thread.currentThread().interrupt()
            return failure("terminal", "TOOL_CANCELLED: Command execution was cancelled.")
        } finally {
            cancellationRegistration?.close()
        }
        return ToolResult(
            "terminal",
            process.exitValue() == 0,
            output.toString(),
            if (process.exitValue() == 0) null else "Exit ${process.exitValue()}",
        )
    }

    private fun shellCommand(command: String): List<String> {
        val shell =
            listOf("zsh", "bash", "sh")
                .firstOrNull(::isExecutableOnPath) ?: "sh"
        return listOf(shell, "-lc", command)
    }

    private fun destroyProcessTree(process: Process) {
        process.toHandle().descendants().forEach { descendant -> descendant.destroyForcibly() }
        process.destroyForcibly()
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

private fun terminalWorkspaceRoot(): Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()

/**
 * Placeholder tool for browser automation when no browser backend is configured.
 *
 * Use cases: UC-0000044.
 */
@AgentTool
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
@AgentTool
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
@AgentTool
class SleepTool : LiveVisualAgentTool {
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

    override fun executeLiveReactive(
        inputJson: String,
        context: Map<String, Any>,
        reporter: ToolLiveReporter,
    ): reactor.core.publisher.Mono<ToolResult> =
        reactor.core.publisher.Mono.defer {
            val seconds = (parseObject(inputJson).int("seconds") ?: 1).coerceIn(0, 300)
            reporter.report(
                de.heckenmann.visualagent.model.ToolLiveState(
                    executionId = "",
                    revision = 0,
                    status = "Waiting",
                    deadlineEpochMillis =
                        java.time.Instant
                            .now()
                            .plusSeconds(seconds.toLong())
                            .toEpochMilli(),
                ),
            )
            reactor.core.publisher.Mono
                .delay(java.time.Duration.ofSeconds(seconds.toLong()))
                .map { success("sleep", "slept ${seconds}s") }
        }
}
