package de.heckenmann.visualagent.release.packaging

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests the actual upstream-runtime verification script with local command fixtures, without starting Docker. */
@Tag("release-integration")
@DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX AppImage build script contract")
class AppImageVerifierIntegrationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `runtime verifier copies verified output to the exact package runtime path`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val archive =
            repository.resolve(
                "modules/desktop/build/reports/dependency-license/library-source-materials/archives/appimage-runtime/type2-runtime-dd6cebed.tar.gz",
            )
        val bin = Files.createDirectory(root.resolve("bin"))

        fun executable(
            name: String,
            content: ByteArray,
        ) {
            val path = Files.write(bin.resolve(name), content)
            assertTrue(path.toFile().setExecutable(true))
        }
        executable("docker", javaClass.getResourceAsStream("/release/docker-fixture.sh")!!.use { it.readBytes() })
        executable("file", "#!/bin/sh\necho 'runtime: ELF 64-bit LSB pie executable, x86-64'\n".toByteArray())
        executable("readelf", "#!/bin/sh\nexit 0\n".toByteArray())
        val target = root.resolve("package/runtime-x86_64")
        val log = root.resolve("command.log")
        val process =
            ProcessBuilder(
                "bash",
                repository.resolve("scripts/verify-appimage-runtime-build.sh").toString(),
                archive.toString(),
                target.toString(),
            ).redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .apply {
                    environment()["PATH"] = "$bin${java.io.File.pathSeparator}${System.getenv("PATH")}"
                }.start()
        process.outputStream.close()
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Runtime verifier fixture timed out")
            assertEquals(0, process.exitValue(), Files.readString(log))
        } finally {
            if (process.isAlive) {
                process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(Files.readString(target).contains("AppImage runtime version: https://github.com/AppImage/type2-runtime/commit/dd6cebe"))
        assertTrue(Files.isExecutable(target))
    }
}
