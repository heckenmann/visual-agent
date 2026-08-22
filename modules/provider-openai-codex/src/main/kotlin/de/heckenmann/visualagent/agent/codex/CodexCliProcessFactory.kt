package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Starts bounded Codex CLI child processes with a sanitized environment.
 */
@Component
internal class CodexCliProcessFactory {
    /**
     * Starts a command and captures bounded stdout and stderr without merging the streams.
     *
     * @param command Executable followed by literal arguments
     * @param workingDirectory Deliberate child working directory, when needed
     * @param timeoutSeconds Maximum process duration in seconds
     * @param onOutput Optional bounded cumulative output callback invoked from [Dispatchers.IO]
     * @return Exit, timeout, and bounded output data
     */
    suspend fun run(
        command: List<String>,
        workingDirectory: Path? = null,
        timeoutSeconds: Long,
        maxOutputCharacters: Int = MAX_OUTPUT_CHARACTERS,
        onOutput: suspend (String) -> Unit = {},
    ): CodexCliProcessResult {
        require(command.isNotEmpty()) { "Codex command must include an executable" }
        require(timeoutSeconds > 0) { "Codex timeout must be positive" }
        require(maxOutputCharacters > 0) { "Codex output limit must be positive" }
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val process =
                    ProcessBuilder(command)
                        .apply {
                            workingDirectory?.let { directory(it.toFile()) }
                            environment().remove(OPENAI_API_KEY)
                            environment().remove(OPENAI_CODEX_API_KEY)
                        }.start()
                try {
                    val progress = StringBuilder()
                    val progressMutex = Mutex()
                    val publish: suspend (String) -> Unit = { chunk ->
                        progressMutex.withLock {
                            val remaining = maxOutputCharacters - progress.length
                            if (remaining > 0) progress.append(chunk.take(remaining))
                            onOutput(progress.toString())
                        }
                    }
                    val stdout = async(Dispatchers.IO) { process.inputStream.readBounded(publish, maxOutputCharacters) }
                    val stderr = async(Dispatchers.IO) { process.errorStream.readBounded(publish, maxOutputCharacters) }
                    val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                    if (!completed) terminateTree(process)
                    CodexCliProcessResult(
                        exitCode = process.exitValueOrNull(),
                        timedOut = !completed,
                        stdout = stdout.await(),
                        stderr = stderr.await(),
                    )
                } finally {
                    if (process.isAlive) terminateTree(process)
                }
            }
        }
    }

    private fun Process.exitValueOrNull(): Int? = if (isAlive) null else exitValue()

    private fun terminateTree(process: Process) {
        val handles =
            process
                .toHandle()
                .descendants()
                .toList()
                .asReversed() + process.toHandle()
        handles.forEach(ProcessHandle::destroy)
        process.waitFor(TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        handles.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    }

    private suspend fun java.io.InputStream.readBounded(
        onChunk: suspend (String) -> Unit,
        maxOutputCharacters: Int,
    ): CodexCliProcessOutput {
        val bytes = ByteArray(BUFFER_SIZE)
        val output = StringBuilder()
        var truncated = false
        while (true) {
            val count = read(bytes)
            if (count < 0) break
            val remaining = maxOutputCharacters - output.length
            if (remaining <= 0) {
                truncated = true
                continue
            }
            val decoded = bytes.decodeToString(0, count)
            output.append(decoded.take(remaining))
            onChunk(decoded.take(remaining))
            truncated = truncated || decoded.length > remaining
        }
        return CodexCliProcessOutput(output.toString(), truncated)
    }

    private companion object {
        private const val OPENAI_API_KEY = "OPENAI_API_KEY"
        private const val OPENAI_CODEX_API_KEY = "OPENAI_CODEX_API_KEY"
        private const val BUFFER_SIZE = 4096
        private const val MAX_OUTPUT_CHARACTERS = 16_384
        private const val TERMINATION_GRACE_MILLIS = 250L
    }
}

/**
 * Bounded output captured from one Codex CLI stream.
 *
 * @property text Decoded UTF-8 output, limited to a fixed maximum length
 * @property truncated Whether data beyond the output limit was discarded
 */
internal data class CodexCliProcessOutput(
    val text: String,
    val truncated: Boolean,
)

/**
 * Completion data for a bounded Codex CLI command.
 *
 * @property exitCode Process exit code, or null when termination prevented a normal exit
 * @property timedOut Whether the process exceeded its duration limit
 * @property stdout Captured standard output
 * @property stderr Captured standard error
 */
internal data class CodexCliProcessResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val stdout: CodexCliProcessOutput,
    val stderr: CodexCliProcessOutput,
)

/**
 * Validates a Codex executable by executing its version command in a safe child process.
 */
@Component
internal class ProcessCodexCliVersionProbe(
    private val processFactory: CodexCliProcessFactory,
) : CodexCliVersionProbe {
    override suspend fun probe(executable: Path): String? {
        val result = processFactory.run(listOf(executable.toString(), "--version"), timeoutSeconds = VERSION_TIMEOUT_SECONDS)
        if (result.timedOut || result.exitCode != 0 || result.stdout.truncated) return null
        return result.stdout.text
            .trim()
            .lineSequence()
            .firstOrNull()
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_VERSION_LENGTH)
    }

    private companion object {
        private const val VERSION_TIMEOUT_SECONDS = 10L
        private const val MAX_VERSION_LENGTH = 128
    }
}
