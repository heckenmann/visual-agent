package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CodexCliAccountServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `status and confirmed account actions use official cli commands`() =
        runBlocking {
            val executable = fakeCodex()
            val processFactory = CodexCliProcessFactory()
            val locator = CodexCliLocator(emptyEnvironment(), ProcessCodexCliVersionProbe(processFactory))
            val service = CodexCliAccountService(locator, processFactory, releaseVersion("rust-v0.146.0"))

            assertEquals(CodexLoginStatus.SIGNED_IN, service.status(executable.toString()))
            assertTrue(service.login(executable.toString()).successful)
            assertTrue(service.deviceLogin(executable.toString()).successful)
            assertTrue(service.logout(executable.toString()).successful)
        }

    @Test
    fun `missing cli returns stable account errors`() =
        runBlocking {
            val processFactory = CodexCliProcessFactory()
            val service =
                CodexCliAccountService(
                    CodexCliLocator(emptyEnvironment(), ProcessCodexCliVersionProbe(processFactory)),
                    processFactory,
                    releaseVersion("rust-v0.146.0"),
                )

            assertEquals(CodexLoginStatus.CLI_MISSING, service.status(tempDir.resolve("missing").toString()))
            assertFalse(service.login(tempDir.resolve("missing").toString()).successful)
        }

    @Test
    fun `version info compares installed and latest official releases`() =
        runBlocking {
            val executable = fakeCodex()
            val processFactory = CodexCliProcessFactory()
            val locator = CodexCliLocator(emptyEnvironment(), ProcessCodexCliVersionProbe(processFactory))

            val update =
                CodexCliAccountService(locator, processFactory, releaseVersion("rust-v0.146.0"))
                    .versionInfo(executable.toString())
            val current =
                CodexCliAccountService(locator, processFactory, releaseVersion("rust-v0.142.5"))
                    .versionInfo(executable.toString())
            val offline =
                CodexCliAccountService(locator, processFactory, releaseVersion(null))
                    .versionInfo(executable.toString())

            assertEquals(CodexCliVersionInfo("0.142.5", "0.146.0", CodexCliUpdateStatus.UPDATE_AVAILABLE), update)
            assertEquals(CodexCliUpdateStatus.UP_TO_DATE, current.status)
            assertEquals(CodexCliUpdateStatus.LATEST_UNAVAILABLE, offline.status)
        }

    @Test
    fun `process runner captures separate output and terminates timeout`() =
        runBlocking {
            val factory = CodexCliProcessFactory()
            val completed =
                factory.run(
                    listOf("/bin/sh", "-c", "printf output; printf error >&2"),
                    timeoutSeconds = 5,
                )
            assertEquals(0, completed.exitCode)
            assertEquals("output", completed.stdout.text)
            assertEquals("error", completed.stderr.text)
            assertFalse(completed.timedOut)

            val timedOut = factory.run(listOf("/bin/sh", "-c", "read ignored"), timeoutSeconds = 1)
            assertTrue(timedOut.timedOut)
        }

    private fun fakeCodex(): Path =
        tempDir.resolve("codex").also { executable ->
            Files.writeString(
                executable,
                """
                #!/bin/sh
                if [ "${'$'}1" = "--version" ]; then printf 'codex-cli 0.142.5\n'; exit 0; fi
                if [ "${'$'}1" = "login" ] && [ "${'$'}2" = "status" ]; then exit 0; fi
                if [ "${'$'}1" = "login" ]; then printf 'login completed\n'; exit 0; fi
                if [ "${'$'}1" = "logout" ]; then printf 'logout completed\n'; exit 0; fi
                exit 1
                """.trimIndent(),
            )
            executable.toFile().setExecutable(true)
        }

    private fun emptyEnvironment(): CodexCliEnvironment =
        object : CodexCliEnvironment {
            override fun pathDirectories(): List<Path> = emptyList()

            override fun homeDirectory(): Path = tempDir

            override fun isWindows(): Boolean = false
        }

    private fun releaseVersion(version: String?): CodexCliReleaseVersionSource = CodexCliReleaseVersionSource { version }
}
