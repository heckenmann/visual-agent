package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.ContextWindow
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.RequestContextBudgeter
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.createClient
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.springResponse
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest(properties = ["visual-agent.ui.enabled=false", "visual-agent.db.path=jdbc:h2:mem:test"])
internal class CodexSpringWiringTest {
    @Autowired
    private lateinit var provider: ConfiguredLLMProvider

    @Autowired
    private lateinit var ollamaClient: OllamaClient

    @Autowired
    private lateinit var openAiClient: OpenAiClient

    @Autowired
    private lateinit var appConfig: AppConfigBean

    @Autowired
    private lateinit var agentManager: AgentManager

    @Autowired
    private lateinit var agentToolConfigService: AgentToolConfigService

    @Autowired
    private lateinit var providerToolCallbacks: ProviderToolCallbacks

    @Autowired
    private lateinit var toolRegistry: ToolRegistry

    @Autowired
    private lateinit var profiledAdapters: List<ProfiledProviderAdapter>

    @Test
    fun `application consumes provider module beans with application adapters`() {
        assertNotNull(profiledAdapters.singleOrNull { it.adapter == ProviderAdapter.CODEX_CLI })
        assertNotNull(provider)
        assertSame(appConfig, injectedAppConfig(ollamaClient))
        assertSame(appConfig, injectedAppConfig(openAiClient))
    }

    @Test
    fun `request budget retains the previous assistant response with real main-agent tool schemas`() {
        val previousMarkdown =
            listOf(
                "```markdown",
                "# Heading",
                "- First",
                "- Second",
                "**Done**",
                "```",
            ).joinToString("\n")
        val conversation =
            listOf(
                Message("user", "Create a five-line Markdown example"),
                Message("assistant", previousMarkdown),
                Message("user", "Explain that"),
            )
        val request =
            agentManager.conversationOps
                .buildMainRequest(conversation)
                .copy(contextWindow = ContextWindow(configuredLimit = 4096))
        val callbacks = providerToolCallbacks.functionCallbacks(agentToolConfigService.mainAgentTools())
        val modelMessages =
            if (callbacks.isEmpty()) {
                request.messages
            } else {
                listOf(Message("system", "Tool timeout contract: ${providerToolCallbacks.toolRuntimeGuidance()}")) + request.messages
            }
        val toolDefinitions =
            callbacks.map { callback ->
                ToolDefinition(
                    id =
                        de.heckenmann.visualagent.agent
                            .ToolId(callback.toolDefinition.name()),
                    name = callback.toolDefinition.name(),
                    description = callback.toolDefinition.description(),
                    inputSchema = callback.toolDefinition.inputSchema(),
                )
            }
        val helpDefinition = toolDefinitions.single { it.name == "tool_help" }
        val fallbackGuard =
            Message("system", "To list available tools, call tool_help with {\"action\":\"list\"}.", id = "__provider_tool_guard__")
        val fullGuard =
            Message("system", "Available functions: ${toolDefinitions.joinToString { it.name }}", id = "__provider_tool_guard__")
        val budgetPlan =
            RequestContextBudgeter().fitWithToolFallback(
                request = request,
                fallbackMessages = listOf(fallbackGuard) + modelMessages.drop(1),
                fallbackTools = listOf(helpDefinition),
                fullMessages = listOf(fullGuard) + modelMessages.drop(1),
                fullTools = toolDefinitions,
            )
        val bounded = budgetPlan.request
        val toolPayload =
            toolDefinitions.joinToString("\n") { tool ->
                "${tool.name}: ${tool.description}\n${tool.inputSchema}"
            }
        val toolTokenCount = JTokkitTokenCountEstimator().estimate(toolPayload)

        assertTrue(
            bounded.messages.any { it.role == "assistant" && it.content == previousMarkdown },
            "The prior assistant turn was dropped: ${toolDefinitions.size} main tools use $toolTokenCount tokens; " +
                "retained messages=${bounded.messages.map {
                    it.role to
                        it.content.take(
                            100,
                        )
                }}, outputLimit=${bounded.parameters.maxTokens}",
        )
        assertTrue(bounded.messages.any { it.role == "user" && it.content == "Explain that" })
        assertEquals(setOf("tool_help"), budgetPlan.toolNames)
        assertTrue(budgetPlan.status.toolSchemasReduced)
    }

    @Test
    fun `Ollama fallback lists and creates agents at 4096 tokens`() {
        val model = mockk<ChatModel>()
        val client = createClient(model, mockk(), toolRegistry, appConfig)
        val name = "Fallback onboarding researcher"
        val request =
            agentManager.conversationOps
                .buildMainRequest(listOf(Message("user", "Create a sub-agent named $name.")))
                .copy(contextWindow = ContextWindow(configuredLimit = 4096), model = "test")
        every { model.call(any<Prompt>()) } answers {
            val prompt = firstArg<Prompt>()
            val options = prompt.options as ToolCallingChatOptions
            assertEquals(listOf("tool_help"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
            val results = prompt.instructions.filterIsInstance<ToolResponseMessage>()
            when (results.size) {
                0 -> helpCall("list", """{"action":"call","name":"agent_list","arguments":{}}""")
                1 -> {
                    assertTrue(
                        results
                            .single()
                            .responses
                            .single()
                            .responseData()
                            .contains("Jobs: active="),
                    )
                    helpCall(
                        "create",
                        """{"action":"call","name":"agent_create","arguments":{"name":"$name","role":"Research topics"}}""",
                    )
                }
                else -> {
                    assertTrue(
                        results
                            .last()
                            .responses
                            .single()
                            .responseData()
                            .contains("Created agent"),
                    )
                    springResponse("test", "Created the researcher.")
                }
            }
        }
        try {
            val response = client.chatReactive(request).block(Duration.ofSeconds(15))
            assertEquals("Created the researcher.", response?.message?.content)
            assertEquals("Research topics", agentManager.getSubAgents().single { it.name == name }.role)
        } finally {
            agentManager.getSubAgents().filter { it.name == name }.forEach { agentManager.deleteAgent(it.id) }
        }
    }

    private fun helpCall(
        id: String,
        arguments: String,
    ): ChatResponse =
        ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder().toolCalls(listOf(AssistantMessage.ToolCall(id, "function", "tool_help", arguments))).build(),
                ),
            ),
        )

    private fun injectedAppConfig(bean: Any): Any? {
        val field = bean.javaClass.getDeclaredField("appConfig")
        field.isAccessible = true
        return field.get(bean)
    }
}
