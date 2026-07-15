package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.config.AppConfigBean
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Generates a friendly welcome message after a conversation reset.
 *
 * The generated message respects the user's custom model instruction so the
 * greeting language, tone, and content follow the configured preferences. If the
 * provider is unreachable, the configured model is not available, or the chat request
 * fails, a static fallback greeting is persisted so the conversation is never left empty.
 */
@Component
class WelcomeMessageComposer(
    private val llmProvider: LLMProvider,
    private val appConfig: AppConfigBean,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Composes and persists the post-reset welcome message.
     *
     * @param persist Callback that stores the resulting assistant message
     * @return Result indicating whether the greeting was generated or a fallback was used
     */
    suspend fun compose(persist: (Message) -> Message): WelcomeResult {
        val userInstruction =
            appConfig
                .userModelInstruction
                .trim()

        if (!llmProvider.checkConnection()) {
            logger.warn { "Welcome generation skipped: provider is not reachable" }
            val fallback = persistFallback(persist, userInstruction)
            return WelcomeResult.Fallback(fallback, IllegalStateException("Provider not reachable"))
        }

        val configuredModel = appConfig.activeModel()
        val availableModels = runCatching { llmProvider.getModels() }.getOrDefault(emptyList())
        if (configuredModel.isNotBlank() && configuredModel !in availableModels) {
            logger.warn { "Welcome generation skipped: model '$configuredModel' is not available" }
            val fallback = persistFallback(persist, userInstruction)
            return WelcomeResult.Fallback(
                fallback,
                IllegalStateException("Model '$configuredModel' is not available"),
            )
        }
        val messages = mutableListOf<Message>()
        messages +=
            Message(
                role = "system",
                content = buildWelcomePrompt(userInstruction).trimIndent(),
            )
        val request =
            ChatRequestContext(
                messages = messages,
                enabledTools = emptySet(),
                metadata = mapOf("sessionId" to AgentManager.MAIN_SESSION_ID, "agent" to "main"),
            )
        val generated =
            runCatching {
                llmProvider
                    .chat(request)
                    .message
                    .content
                    .trim()
            }.getOrElse { error ->
                logger.warn(error) { "Welcome generation failed; using fallback greeting" }
                null
            }
        val welcome = generated?.takeIf { it.isNotBlank() } ?: fallbackWelcome(userInstruction)
        persist(Message(role = "assistant", content = welcome))
        return if (generated.isNullOrBlank()) {
            WelcomeResult.Fallback(welcome, IllegalStateException("Provider chat request failed"))
        } else {
            WelcomeResult.Generated(welcome)
        }
    }

    private fun persistFallback(
        persist: (Message) -> Message,
        userInstruction: String,
    ): String {
        val fallback = fallbackWelcome(userInstruction)
        persist(Message(role = "assistant", content = fallback))
        return fallback
    }

    private fun fallbackWelcome(userInstruction: String): String {
        val base =
            "Hello! I'm ready to help with your project tasks. I can coordinate worker agents, " +
                "manage todos, read files, run terminal commands, and answer questions about your workspace."
        return if (userInstruction.isBlank()) {
            base
        } else {
            "$base\n\nUser preferences:\n$userInstruction"
        }
    }

    private fun buildWelcomePrompt(userInstruction: String): String {
        val instructionBlock =
            if (userInstruction.isBlank()) {
                ""
            } else {
                "\n\nRespect these user preferences for the entire response:\n$userInstruction"
            }
        return """
            You are Visual Agent.
            Greet the user after a conversation reset in a friendly, concise way.
            Then list in short bullet points what you can do in this app as the main orchestrator:
            - coordinate worker agents
            - create, update, delete, and assign sub-agents
            - review worker results
            - answer project questions from the available conversation context
            - keep the task plan moving through worker delegation
            Keep it concise and friendly.$instructionBlock
            """
    }
}
