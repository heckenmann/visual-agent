package de.heckenmann.visualagent.release

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/** Enforces the repository tooling language policy for tracked and newly added source files. */
class RepositoryLanguagePolicyTest {
    @Test
    fun `repository contains no forbidden script files or interpreter entry points`() {
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val process =
            ProcessBuilder("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z")
                .directory(repository.toFile())
                .start()
        val names =
            process.inputStream
                .bufferedReader()
                .readText()
                .split('\u0000')
                .filter { it.isNotEmpty() }
        assertTrue(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0, "Cannot inventory repository files")
        val interpreter = "py" + "thon"
        val suffixes = setOf("py", "pyw", "pyc", "pyo", "pyz")
        val invocation = Regex("""(?m)(?:^\s*(?:run:\s*|exec\s+)?|[;|&]\s*|["'])$interpreter(?:[23](?:\.\d+)?)?(?:\.exe)?(?:[\s"']|$)""")
        val setupAction = "setup-" + interpreter
        val violations = mutableListOf<String>()
        for (name in names) {
            val file = repository.resolve(name)
            if (!Files.isRegularFile(file)) continue
            if (file.fileName
                    .toString()
                    .substringAfterLast('.', "")
                    .lowercase() in suffixes
            ) {
                violations.add(name)
            }
            if (name.substringAfterLast('.', "") !in setOf("kt", "kts", "sh", "ps1", "yml", "yaml")) continue
            val text = Files.readString(file)
            if (invocation.containsMatchIn(text) ||
                text.contains(setupAction) ||
                Regex("""(?m)^#!.*\b$interpreter""").containsMatchIn(text)
            ) {
                violations.add("$name: forbidden interpreter invocation")
            }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
