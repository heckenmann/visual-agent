package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that the published Codex Agent API is invoked end to end without a local protocol client. */
class CodexAgentBridgeTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `maps the published agent response`() =
        runBlocking {
            val executable = temporaryDirectory.resolve("fake-codex")
            Files.writeString(
                executable,
                "#!/bin/sh\n" +
                    "if [ \"${'$'}1\" = \"--version\" ]; then printf 'codex-cli test\\n'; exit 0; fi\n" +
                    "if [ -n \"${'$'}OPENAI_API_KEY\" ] || [ -n \"${'$'}OPENAI_CODEX_API_KEY\" ]; then exit 42; fi\n" +
                    "printf 'published-agent-response\\n'\n",
            )
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }

            val result = CodexAgentBridge(executable, temporaryDirectory, "default").complete(Prompt("hello"))

            assertEquals("published-agent-response", result.content.trim())
        }

    @Test
    fun `extracts the completed agent message from cli json output`() {
        val output =
            """{"type":"item.completed","item":{"type":"agent_message","text":"published-agent-response"}}"""

        assertEquals("published-agent-response", output.extractAgentText())
    }

    @Test
    fun `uses the effective model reported by the cli`() =
        runBlocking {
            val executable = temporaryDirectory.resolve("fake-codex")
            Files.writeString(
                executable,
                "#!/bin/sh\n" +
                    "if [ \"${'$'}1\" = \"--version\" ]; then printf 'codex-cli test\\n'; exit 0; fi\n" +
                    "printf 'model: rerouted-model\\n'\n" +
                    "printf 'published-agent-response\\n'\n",
            )
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }

            val result = CodexAgentBridge(executable, temporaryDirectory, "requested-model").complete(Prompt("hello"))

            assertEquals("rerouted-model", result.model)
        }

    @Test
    fun `cancellation interrupts an in-flight cli execution`() =
        runBlocking {
            val executable = temporaryDirectory.resolve("slow-codex")
            Files.writeString(
                executable,
                "#!/bin/sh\n" +
                    "if [ \"${'$'}1\" = \"--version\" ]; then printf 'codex-cli test\\n'; exit 0; fi\n" +
                    "sleep 30\n",
            )
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }
            val token = CancellationToken()
            supervisorScope {
                val request =
                    async(Dispatchers.IO) {
                        CodexAgentBridge(executable, temporaryDirectory, "requested-model").complete(Prompt("hello"), token)
                    }

                delay(100)
                token.cancel()
                val result = runCatching { withTimeout(3_000) { request.await() } }

                assertTrue(result.isFailure)
            }
        }

    @Test
    @EnabledIfSystemProperty(named = "visualagent.codex.smoke", matches = "true")
    fun `returns an assistant response from the installed codex cli`() =
        runBlocking {
            val executable = Path.of(requireNotNull(System.getProperty("visualagent.codex.smoke.executable")))
            val model = requireNotNull(System.getProperty("visualagent.codex.smoke.model"))
            val workingDirectory = Path.of(requireNotNull(System.getProperty("user.dir")))
            val result = CodexAgentBridge(executable, workingDirectory, model).complete(Prompt("Reply with exactly: smoke-ok"))

            assertEquals("smoke-ok", result.content.trim())
        }
}
