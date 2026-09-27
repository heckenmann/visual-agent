package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Bounded result from one explicitly constructed diagnostic subprocess invocation. */
data class DiagnosticProcessResult(
    /** Exit code, when the process started and exited before its deadline. */
    val exitCode: Int?,
    /** Captured stdout/stderr prefix, bounded by the requested character limit. */
    val output: String,
    /** Whether the process exceeded its supplied overall timeout. */
    val timedOut: Boolean,
    /** Whether the executable could not be started. */
    val executableUnavailable: Boolean,
    /** Whether the caller cancelled execution by interrupting the waiting thread. */
    val cancelled: Boolean = false,
)

/** Executes a fixed-argument diagnostic process with bounded time and captured output. */
fun interface DiagnosticProcessRunner {
    /**
     * Run a prevalidated argument vector without a shell.
     *
     * @param command Executable followed by individually validated arguments
     * @param timeoutMillis Overall process deadline
     * @param maxOutputCharacters Maximum captured output size
     * @return Bounded process result
     */
    fun run(
        command: List<String>,
        timeoutMillis: Long,
        maxOutputCharacters: Int,
    ): DiagnosticProcessResult
}

/** JVM implementation that destroys the process tree on timeout and drains bounded output. */
@Component
class JvmDiagnosticProcessRunner : DiagnosticProcessRunner {
    override fun run(
        command: List<String>,
        timeoutMillis: Long,
        maxOutputCharacters: Int,
    ): DiagnosticProcessResult {
        require(command.isNotEmpty() && command.none(String::isBlank))
        require(timeoutMillis > 0 && maxOutputCharacters > 0)
        val process =
            try {
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                val childEnvironment = builder.environment()
                childEnvironment.clear()
                childEnvironment.putAll(diagnosticChildEnvironment(System.getenv()))
                builder.start()
            } catch (_: IOException) {
                return DiagnosticProcessResult(null, "", timedOut = false, executableUnavailable = true)
            } catch (_: SecurityException) {
                return DiagnosticProcessResult(null, "", timedOut = false, executableUnavailable = true)
            }

        val captured = StringBuilder(minOf(maxOutputCharacters, OUTPUT_INITIAL_CAPACITY))
        val reader = Thread.ofVirtual().start { drainOutput(process.inputStream, captured, maxOutputCharacters) }
        val completed =
            try {
                process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.interrupted()
                terminateTree(process)
                joinOutputReader(reader, process)
                Thread.currentThread().interrupt()
                return DiagnosticProcessResult(null, snapshot(captured), timedOut = false, executableUnavailable = false, cancelled = true)
            }
        if (!completed) terminateTree(process)
        joinOutputReader(reader, process)
        return DiagnosticProcessResult(
            exitCode = process.takeIf { completed }?.exitValue(),
            output = snapshot(captured),
            timedOut = !completed,
            executableUnavailable = false,
        )
    }

    private fun drainOutput(
        input: InputStream,
        captured: StringBuilder,
        maxOutputCharacters: Int,
    ) {
        input.use { stream ->
            val buffer = ByteArray(OUTPUT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) return
                synchronized(captured) {
                    if (captured.length < maxOutputCharacters) {
                        val allowed = minOf(count, maxOutputCharacters - captured.length)
                        captured.append(String(buffer, 0, allowed, Charsets.UTF_8))
                    }
                }
            }
        }
    }

    private fun joinOutputReader(
        reader: Thread,
        process: Process,
    ) {
        try {
            reader.join(OUTPUT_READER_JOIN_MILLIS)
            if (reader.isAlive) {
                process.inputStream.close()
                reader.interrupt()
                reader.join(OUTPUT_READER_JOIN_MILLIS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            reader.interrupt()
        } catch (_: IOException) {
            reader.interrupt()
        }
    }

    private fun terminateTree(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        try {
            process.waitFor(PROCESS_TERMINATION_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun snapshot(captured: StringBuilder): String = synchronized(captured) { captured.toString() }

    private companion object {
        const val OUTPUT_BUFFER_SIZE = 1024
        const val OUTPUT_INITIAL_CAPACITY = 1024
        const val OUTPUT_READER_JOIN_MILLIS = 250L
        const val PROCESS_TERMINATION_WAIT_MILLIS = 500L
    }
}

/** Builds the minimal environment passed to host diagnostic utilities. */
internal fun diagnosticChildEnvironment(parentEnvironment: Map<String, String>): Map<String, String> =
    parentEnvironment
        .filterKeys { it.uppercase() in SAFE_PROCESS_ENVIRONMENT_KEYS }
        .toMutableMap()
        .apply { put("LC_ALL", "C") }

private val SAFE_PROCESS_ENVIRONMENT_KEYS = setOf("PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "TMPDIR")
