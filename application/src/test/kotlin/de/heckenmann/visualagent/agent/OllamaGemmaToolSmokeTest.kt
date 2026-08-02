package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertTrue

/**
 * Local smoke test for real Ollama tool calling.
 *
 * This test is opt-in because it requires a running Ollama daemon and the
 * `gemma4:e2b` model to be present locally.
 */
@SpringBootTest(properties = ["visual-agent.ui.enabled=false"])
@EnabledIfSystemProperty(named = "visualagent.ollama.smoke", matches = "true")
class OllamaGemmaToolSmokeTest {
    @Autowired
    private lateinit var llmProvider: LLMProvider

    @Autowired
    private lateinit var toolEventBus: ToolEventBus

    /**
     * Verifies that gemma4:e2b can call the todos tool through the Spring AI provider path.
     */
    @Test
    fun `gemma4 e2b can call todos tool through spring ai`() =
        runBlocking {
            val events = CopyOnWriteArrayList<ToolCallEvent>()
            val listener = toolEventBus.addListener { events += it }
            try {
                val response =
                    llmProvider.chat(
                        ChatRequestContext(
                            messages =
                                listOf(
                                    Message(
                                        role = "user",
                                        content = "Use the todos tool with action count. Do not answer from memory.",
                                    ),
                                ),
                            model = "gemma4:e2b",
                            enabledTools = setOf(ToolId("todos")),
                            metadata = mapOf("sessionId" to "main", "agent" to "tool-smoke-test"),
                        ),
                    )

                println("[OllamaGemmaToolSmokeTest] response=${response.message.content}")
                println("[OllamaGemmaToolSmokeTest] events=${events.map { "${it.phase}:${it.toolId}:${it.result.content}" }}")
                assertTrue(events.any { it.phase == ToolCallPhase.FINISHED && it.toolId == "todos" })
            } finally {
                listener.close()
            }
        }
}
