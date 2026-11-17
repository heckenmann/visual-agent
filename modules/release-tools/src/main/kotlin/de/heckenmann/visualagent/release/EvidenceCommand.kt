package de.heckenmann.visualagent.release

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Executes an evidence query with bounded time/output and removes its exact temporary files. */
internal fun runEvidenceCommand(
    command: List<String>,
    output: Path,
    timeoutSeconds: Long = 15,
): String {
    val directory = Files.createTempDirectory(output, ".evidence-command-")
    val stdout = directory.resolve("stdout")
    val stderr = directory.resolve("stderr")
    var process: Process? = null
    try {
        process = ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        process.outputStream.close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            require(System.nanoTime() < deadline) { "Evidence command timed out" }
            require(Files.size(stdout) + Files.size(stderr) <= MAX_NOTICE_BYTES) { "Evidence command output limit exceeded" }
        }
        require(process.exitValue() == 0) { "Evidence command failed with exit code ${process.exitValue()}" }
        require(Files.size(stdout) + Files.size(stderr) <= MAX_NOTICE_BYTES) { "Evidence command output limit exceeded" }
        return Files.readString(stdout) + Files.readString(stderr)
    } finally {
        process?.let { child ->
            child.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
            if (child.isAlive) {
                child.destroyForcibly()
                child.waitFor(5, TimeUnit.SECONDS)
            }
        }
        Files.deleteIfExists(stdout)
        Files.deleteIfExists(stderr)
        Files.delete(directory)
    }
}
