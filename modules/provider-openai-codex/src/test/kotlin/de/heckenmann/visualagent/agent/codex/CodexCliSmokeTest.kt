package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path
import kotlin.test.assertTrue

/** Runs the opt-in authenticated Codex app-server smoke test. */
@EnabledIfSystemProperty(named = "visualagent.codex.smoke", matches = "true")
class CodexCliSmokeTest {
    @Test
    fun `authenticated Codex app server returns a response`() =
        runBlocking {
            val model =
                System
                    .getProperty("visualagent.codex.smoke.model")
                    ?.takeIf(String::isNotBlank)
                    ?: error("Set visualagent.codex.smoke.model for the Codex smoke test")
            val executable =
                Path.of(
                    System
                        .getProperty("visualagent.codex.smoke.executable")
                        ?.takeIf(String::isNotBlank)
                        ?: "codex",
                )
            val response =
                CodexAppServerChatModel(
                    executable = executable,
                    model = model,
                    toolCallbacks = emptyList(),
                    workingDirectory = Path.of(System.getProperty("user.dir")),
                ).complete(Prompt("Reply with a short confirmation that the Codex smoke test passed."))

            assertTrue(
                response.result
                    ?.output
                    ?.text
                    .orEmpty()
                    .isNotBlank(),
            )
        }
}
