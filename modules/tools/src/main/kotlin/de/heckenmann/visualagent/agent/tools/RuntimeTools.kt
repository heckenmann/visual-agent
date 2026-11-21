package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Tool that runs bounded, non-interactive shell commands in the workspace.
 *
 * Use cases: UC-0000021.
 */
@AgentTool
class TerminalTool(
    private val launcher: TerminalSandboxLauncher? = null,
) : LiveVisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("terminal"),
            name = ToolId("terminal").toFunctionName(),
            description =
                "Run a non-interactive shell command on the application server. " +
                    "Returns combined stdout and stderr and the exit status.",
            inputSchema =
                """{"type":"object","properties":{"target":{"type":"string","enum":["server"]},"command":{"type":"string"},"grantId":{"type":"string"},"workingDirectory":{"type":"string"}},"required":["target","command"],"additionalProperties":false}""",
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
        val target = input.requiredString("target")
        val host =
            when (target) {
                "server" -> TerminalHost.SERVER
                "client" -> TerminalHost.CLIENT
                else -> return failure("terminal", "INVALID_TARGET: Select server or client.")
            }
        val request = TerminalSandboxRequest(host, command, input.string("grantId"), input.string("workingDirectory"))
        val process =
            launcher?.launch(request, context)
                ?: return failure("terminal", "SANDBOX_UNAVAILABLE: Terminal execution is unavailable on the selected target.")
        val cancellationRegistration =
            (context["toolCancellationToken"] as? ToolCancellationToken)?.onCancelled { process.cancel() }
        val output = StringBuilder()
        val recent = TerminalLiveLog()
        var completed = false
        val exitCode =
            try {
                reporter?.report(
                    de.heckenmann.visualagent.model
                        .ToolLiveState("", 0, "Running command on $target"),
                )
                process.output.reader(StandardCharsets.UTF_8).use { reader ->
                    val chunk = CharArray(512)
                    var count = reader.read(chunk)
                    while (count >= 0) {
                        if (output.length < 8000) output.append(chunk, 0, count.coerceAtMost(8000 - output.length))
                        recent.append(chunk, count)
                        reporter?.report(
                            de.heckenmann.visualagent.model
                                .ToolLiveState("", 0, "Running command", logs = recent.snapshot()),
                        )
                        count = reader.read(chunk)
                    }
                }
                process.awaitExit().also { completed = true }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return failure("terminal", "TOOL_CANCELLED: Command execution was cancelled.")
            } finally {
                cancellationRegistration?.close()
                if (!completed) process.cancel()
            }
        return ToolResult(
            "terminal",
            exitCode == 0,
            "Target: $target\nExit code: $exitCode\n$output",
            if (exitCode == 0) null else "Exit $exitCode",
        )
    }
}

/** Host role selected for a terminal invocation. */
enum class TerminalHost {
    /** The host running the Visual Agent server. */
    SERVER,

    /** The desktop associated with the current request. */
    CLIENT,
}

/**
 * Model input for one sandbox invocation. The selected host must authorize the grant and resolve the working directory.
 */
data class TerminalSandboxRequest(
    /** Requested host role. */
    val target: TerminalHost,
    /** Shell script to execute inside the sandbox. */
    val command: String,
    /** Optional opaque directory grant identifier. */
    val grantId: String?,
    /** Optional working directory relative to the authorized root. */
    val workingDirectory: String?,
)

/** Starts an authorized terminal command with the sandbox enforced on the selected host. */
fun interface TerminalSandboxLauncher {
    /** Returns the running sandbox process with merged output, or null when execution is unavailable. */
    fun launch(
        request: TerminalSandboxRequest,
        context: Map<String, Any>,
    ): TerminalSandboxExecution?
}

/** One terminal invocation on the selected host, including its output and cancellation channel. */
interface TerminalSandboxExecution {
    /** Combined standard output and standard error. */
    val output: InputStream

    /** Waits for the invocation and returns its exit code. */
    fun awaitExit(): Int

    /** Stops the entire sandbox, including descendants, or throws if termination cannot be confirmed. */
    fun cancel()
}

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
