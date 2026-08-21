package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springaicommunity.agents.codexsdk.CodexClient
import org.springaicommunity.agents.codexsdk.types.ApprovalPolicy
import org.springaicommunity.agents.codexsdk.types.ExecuteOptions
import org.springaicommunity.agents.codexsdk.types.SandboxMode
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/** Adapts a Spring AI prompt to the published Spring AI Codex Agent API. */
internal class CodexAgentBridge(
    private val executable: Path,
    private val workingDirectory: Path,
    private val model: String,
) {
    /** Executes one prompt through the library's synchronous Codex client. */
    suspend fun complete(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): CodexAgentResult =
        withContext(Dispatchers.IO) {
            cancellationToken?.throwIfCancelled()
            val clientOptionsBuilder =
                ExecuteOptions
                    .builder()
                    .workingDirectory(workingDirectory)
                    .sandboxMode(SandboxMode.WORKSPACE_WRITE)
                    .approvalPolicy(ApprovalPolicy.SMART)
                    .fullAuto(false)
                    .skipGitCheck(true)
                    .jsonOutput(true)
                    .timeout(OPERATION_TIMEOUT)
            if (model.isNotBlank()) clientOptionsBuilder.model(model)
            val clientOptions = clientOptionsBuilder.build()
            SanitizedCodexExecutable(executable).use { sanitizedExecutable ->
                CodexClient.create(clientOptions, workingDirectory, sanitizedExecutable.path.toString()).use { client ->
                    val executingThread = Thread.currentThread()
                    val cancellationRegistration = cancellationToken?.onCancelled { executingThread.interrupt() }
                    try {
                        cancellationToken?.throwIfCancelled()
                        val response = client.execute(prompt.toAgentGoal(), clientOptions)
                        check(response.isSuccessful) { "Codex agent execution failed (exit code ${response.exitCode})" }
                        CodexAgentResult(
                            response.model.takeIf(String::isNotBlank) ?: model,
                            response.output.extractAgentText(),
                        )
                    } finally {
                        cancellationRegistration?.close()
                    }
                }
            }
        }

    /** Emits the complete library response as one Spring AI-compatible stream item. */
    fun stream(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): Flow<CodexAgentChunk> =
        flow {
            val result = complete(prompt, cancellationToken)
            emit(CodexAgentChunk(result.model, result.content, terminal = true))
        }

    private companion object {
        private val OPERATION_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}

/** Provides a short-lived executable wrapper that strips API-key variables before Codex starts. */
private class SanitizedCodexExecutable(
    executable: Path,
) : AutoCloseable {
    val path: Path
    private val directory: Path

    init {
        directory =
            Files.createTempDirectory("visual-agent-codex-${UUID.randomUUID()}-")
        path = directory.resolve(if (isWindows()) "codex.cmd" else "codex")
        Files.writeString(path, script(executable))
        check(path.toFile().setExecutable(true)) { "Codex executable wrapper could not be enabled" }
    }

    override fun close() {
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
    }

    private fun script(executable: Path): String =
        if (isWindows()) {
            "@echo off\r\nset OPENAI_API_KEY=\r\nset OPENAI_CODEX_API_KEY=\r\ncall \"$executable\" %*\r\n"
        } else {
            "#!/bin/sh\nunset OPENAI_API_KEY OPENAI_CODEX_API_KEY\nexec ${shellQuote(executable.toString())} \"${'$'}@\"\n"
        }

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

private fun Prompt.toAgentGoal(): String =
    getInstructions().joinToString("\n\n") { message ->
        "[${message.messageType.value}]\n${message.text.orEmpty()}"
    }

/** Extracts the final assistant message from the CLI's JSON event stream. */
internal fun String.extractAgentText(): String {
    val response =
        lineSequence()
            .mapNotNull { line -> runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() }
            .mapNotNull { event ->
                val item = event["item"]?.jsonObject ?: return@mapNotNull null
                if (
                    event["type"]?.jsonPrimitive?.contentOrNull == "item.completed" &&
                    item["type"]?.jsonPrimitive?.contentOrNull == "agent_message"
                ) {
                    item["text"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            }.lastOrNull()
            ?.trim()
    return response ?: trim().takeIf(String::isNotBlank) ?: error("Codex returned no agent message")
}

/** Complete response returned by the published Codex Agent API. */
internal data class CodexAgentResult(
    val model: String,
    val content: String,
)

/** One complete-response stream item emitted by the non-streaming library API. */
internal data class CodexAgentChunk(
    val model: String,
    val content: String,
    val terminal: Boolean,
)
