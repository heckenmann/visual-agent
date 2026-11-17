package de.heckenmann.visualagent.release

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the actual Gradle distribution and repository shell entry point. */
@DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX wrapper contract; JVM verification runs on every platform")
class ReleaseToolsCliTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `wrapper accepts valid assets and rejects extra files`() {
        val assets = Files.createDirectory(root.resolve("assets with spaces"))
        ReleaseAssets.requiredNames.forEach { name ->
            val file = assets.resolve(name)
            if (name.endsWith(".zip")) {
                ZipOutputStream(Files.newOutputStream(file)).use {
                    it.putNextEntry(ZipEntry("README.txt"))
                    it.write("Recipient materials".toByteArray())
                    it.closeEntry()
                }
            } else {
                Files.writeString(file, "package")
            }
        }
        assertEquals(0, run("verify-assets", "--assets", assets.toString()).first)
        Files.writeString(assets.resolve("unexpected"), "extra")
        val failure = run("verify-assets", "--assets", assets.toString())
        assertEquals(1, failure.first)
        assertTrue(failure.second.contains("exactly the ten packages"))
    }

    @Test
    fun `bad arguments missing directories and malformed metadata fail closed`() {
        for (args in listOf(
            emptyArray(),
            arrayOf("verify-assets", "--assets", root.resolve("missing").toString()),
            arrayOf("verify-assets", "--assets", root.toString(), "--unknown"),
            arrayOf("verify-assets", "--assets", root.toString(), "--uploaded"),
        )) {
            val result = run(*args)
            assertEquals(1, result.first)
            assertTrue(result.second.contains("Release asset check:"))
        }
    }

    private fun run(vararg args: String): Pair<Int, String> {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val output = root.resolve("command.log")
        val process =
            ProcessBuilder(listOf("bash", repository.resolve("scripts/release-tools.sh").toString()) + args)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()
        process.outputStream.close()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Release CLI exceeded its timeout")
            return process.exitValue() to Files.readString(output)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }
}
