package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexCliLocatorTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `invalid explicit path never falls back to automatic discovery`() =
        runTest {
            val fallback = executable("path/codex")
            val probe = FakeVersionProbe(mapOf(fallback to "codex-cli 1.0.0"))
            val locator = locator(pathDirectories = listOf(fallback.parent), probe = probe)

            val result = locator.locate(temporaryDirectory.resolve("missing-codex").toString())

            assertEquals(CodexCliLocation.InvalidExplicitPath, result)
            assertTrue(probe.probedExecutables.isEmpty())
        }

    @Test
    fun `path executable takes precedence over user local executable`() =
        runTest {
            val pathExecutable = executable("path/codex")
            val userLocalExecutable = executable("home/.local/bin/codex")
            val locator =
                locator(
                    pathDirectories = listOf(pathExecutable.parent),
                    homeDirectory = temporaryDirectory.resolve("home"),
                    probe = FakeVersionProbe(mapOf(pathExecutable to "codex-cli 1.0.0", userLocalExecutable to "codex-cli 2.0.0")),
                )

            val result = locator.locate(null)

            assertIs<CodexCliLocation.Ready>(result)
            assertEquals(pathExecutable, result.executable)
            assertEquals("codex-cli 1.0.0", result.version)
            assertEquals(CodexCliLocationSource.PATH, result.source)
        }

    @Test
    fun `invalid automatic candidate continues to user local candidate`() =
        runTest {
            val pathExecutable = executable("path/codex")
            val userLocalExecutable = executable("home/.local/bin/codex")
            val probe = FakeVersionProbe(mapOf(userLocalExecutable to "codex-cli 2.0.0"))
            val locator =
                locator(
                    pathDirectories = listOf(pathExecutable.parent),
                    homeDirectory = temporaryDirectory.resolve("home"),
                    probe = probe,
                )

            val result = locator.locate(null)

            assertIs<CodexCliLocation.Ready>(result)
            assertEquals(userLocalExecutable, result.executable)
            assertEquals(CodexCliLocationSource.USER_LOCAL, result.source)
            assertEquals(listOf(pathExecutable, userLocalExecutable), probe.probedExecutables)
        }

    @Test
    fun `missing candidates report missing cli`() =
        runTest {
            val locator = locator(pathDirectories = emptyList(), homeDirectory = temporaryDirectory.resolve("home"))

            assertEquals(CodexCliLocation.Missing, locator.locate(null))
        }

    private fun locator(
        pathDirectories: List<Path>,
        homeDirectory: Path = temporaryDirectory.resolve("home"),
        probe: CodexCliVersionProbe = FakeVersionProbe(emptyMap()),
    ): CodexCliLocator =
        CodexCliLocator(
            environment = FakeEnvironment(pathDirectories, homeDirectory),
            versionProbe = probe,
        )

    private fun executable(relativePath: String): Path {
        val candidate = temporaryDirectory.resolve(relativePath)
        Files.createDirectories(candidate.parent)
        Files.writeString(candidate, "placeholder")
        check(candidate.toFile().setExecutable(true)) { "Test executable permission could not be set" }
        return candidate
    }

    private class FakeEnvironment(
        private val pathDirectories: List<Path>,
        private val homeDirectory: Path,
    ) : CodexCliEnvironment {
        override fun pathDirectories(): List<Path> = pathDirectories

        override fun homeDirectory(): Path = homeDirectory

        override fun isWindows(): Boolean = false
    }

    private class FakeVersionProbe(
        private val versions: Map<Path, String>,
    ) : CodexCliVersionProbe {
        val probedExecutables = mutableListOf<Path>()

        override suspend fun probe(executable: Path): String? {
            probedExecutables.add(executable)
            return versions[executable]
        }
    }
}
