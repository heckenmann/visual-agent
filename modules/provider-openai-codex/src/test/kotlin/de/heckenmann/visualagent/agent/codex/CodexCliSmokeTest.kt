package de.heckenmann.visualagent.agent.codex

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path
import kotlin.test.assertTrue

/** Runs the opt-in authenticated Codex app-server smoke test. */
@EnabledIfSystemProperty(named = "visualagent.codex.smoke", matches = "true")
class CodexCliSmokeTest {
    @Test
    fun `authenticated Codex app server forwards text before turn completion`() {
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
        val started = System.nanoTime()
        var firstTextAt: Long? = null
        var completedAt: Long? = null
        val responses =
            CodexAppServerChatModel(
                executable = executable,
                model = model,
                toolCallbacks = emptyList(),
                workingDirectory = Path.of(System.getProperty("user.dir")),
            ).streamReactive(Prompt("Reply in two short sentences about how a poem is written."))
                .doOnNext { response ->
                    if (response.result
                            ?.output
                            ?.text
                            .orEmpty()
                            .isNotEmpty() &&
                        firstTextAt == null
                    ) {
                        firstTextAt = System.nanoTime()
                    }
                    if (response.hasFinishReasons(setOf("stop"))) completedAt = System.nanoTime()
                }.collectList()
                .block()
                .orEmpty()

        assertTrue(
            responses.any {
                it.result
                    ?.output
                    ?.text
                    .orEmpty()
                    .isNotEmpty()
            },
        )
        val firstText = requireNotNull(firstTextAt)
        val completion = requireNotNull(completedAt)
        assertTrue(firstText < completion)
        println(
            "Codex first text after ${(firstText - started) / 1_000_000} ms; completed after ${(completion - started) / 1_000_000} ms",
        )
    }

    @Test
    fun `authenticated Codex app server returns a response`() {
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
            requireNotNull(
                CodexAppServerChatModel(
                    executable = executable,
                    model = model,
                    toolCallbacks = emptyList(),
                    workingDirectory = Path.of(System.getProperty("user.dir")),
                ).completeReactive(Prompt("Reply with a short confirmation that the Codex smoke test passed.")).block(),
            )

        assertTrue(
            response.result
                ?.output
                ?.text
                .orEmpty()
                .isNotBlank(),
        )
    }
}
