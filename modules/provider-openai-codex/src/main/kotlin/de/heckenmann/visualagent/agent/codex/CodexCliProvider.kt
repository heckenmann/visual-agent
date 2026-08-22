package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.provider.ProviderWorkingDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Codex CLI subscription provider backed by the native Codex app-server protocol. */
@Component
class CodexCliProvider internal constructor(
    private val locator: CodexCliLocator,
    private val toolCallbacks: ProviderToolCallbacks,
    private val modelCatalog: CodexModelCatalog,
    private val workingDirectory: ProviderWorkingDirectory = ProviderWorkingDirectory { Path.of(System.getProperty("user.dir")) },
) : ProfiledProviderAdapter {
    override val adapter: ProviderAdapter = ProviderAdapter.CODEX_CLI

    override suspend fun chat(messages: List<Message>): ChatResponse = error("Codex CLI chat requires a configured provider profile")

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
            val model = effectiveModel(request.model ?: profile.defaultModel)
            val response =
                CodexAppServerChatModel(
                    resolveExecutable(profile),
                    model,
                    callbacks(request, model),
                    request.workingDirectory(),
                    request.showReasoningSummary(),
                ).complete(request.toPrompt(), request.cancellationToken)
            ChatResponse(
                model = response.metadata.model.takeIf(String::isNotBlank) ?: model,
                message =
                    Message(
                        "assistant",
                        response.result
                            ?.output
                            ?.text
                            .orEmpty(),
                    ),
                done = true,
            )
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> =
        error("Codex CLI streaming requires a configured provider profile")

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
        val model = effectiveModel(request.model ?: profile.defaultModel)
        return flow {
            CodexAppServerChatModel(
                resolveExecutable(profile),
                model,
                callbacks(request, model),
                request.workingDirectory(),
                request.showReasoningSummary(),
            ).streamFlow(request.toPrompt(), request.cancellationToken).collect { chunk ->
                emit(
                    ChatResponse(
                        model = chunk.metadata.model.takeIf(String::isNotBlank) ?: model,
                        message =
                            Message(
                                "assistant",
                                chunk.result
                                    ?.output
                                    ?.text
                                    .orEmpty(),
                            ),
                        done = chunk.hasFinishReasons(setOf("stop")),
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = error("Codex CLI vision requires a configured provider profile")

    /** Sends an image through the configured Codex app-server profile. */
    override suspend fun vision(
        image: ByteArray,
        prompt: String,
        modelId: String,
        profile: ProviderProfile,
    ): ChatResponse =
        withContext(Dispatchers.IO) {
            val model = effectiveModel(modelId.ifBlank { profile.defaultModel })
            val response =
                CodexAppServerChatModel(
                    resolveExecutable(profile),
                    model,
                    emptyList(),
                    workingDirectory.get(),
                ).completeVision(image, prompt)
            ChatResponse(
                model = response.metadata.model.takeIf(String::isNotBlank) ?: model,
                message =
                    Message(
                        "assistant",
                        response.result
                            ?.output
                            ?.text
                            .orEmpty(),
                    ),
                done = true,
            )
        }

    override suspend fun embeddings(text: String): List<Double> = emptyList()

    override fun isConnected(): Boolean = true

    override suspend fun checkConnection(): Boolean = false

    override suspend fun getModels(): List<String> = error("Codex CLI model discovery is provided by the Codex model catalog")

    override suspend fun getModelDetails(modelName: String): ShowResponse =
        ShowResponse(model = modelName, modifiedAt = "", details = ModelDetails(family = "Codex CLI"))

    override suspend fun getModelDetails(
        profile: ProviderProfile,
        modelName: String,
    ): ShowResponse = getModelDetails(modelName)

    override suspend fun loadModels(profile: ProviderProfile): List<ProviderModelConfig> = modelCatalog.load(profile)

    private suspend fun resolveExecutable(profile: ProviderProfile): Path =
        when (val result = locator.locate(profile.options[OPTION_EXECUTABLE_PATH])) {
            is CodexCliLocation.Ready -> result.executable
            CodexCliLocation.InvalidExplicitPath -> error("Configured Codex CLI path is invalid")
            CodexCliLocation.Missing -> error("Codex CLI is not installed")
        }

    private fun ChatRequestContext.toPrompt(): Prompt =
        Prompt(
            messages.map { message ->
                when (message.role) {
                    "system" -> SystemMessage(message.content)
                    "assistant" -> AssistantMessage(message.content)
                    else -> UserMessage(message.content)
                }
            },
        )

    private fun ChatRequestContext.workingDirectory(): Path =
        metadata["workingDirectory"]
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: workingDirectory.get()

    private fun ChatRequestContext.showReasoningSummary(): Boolean = (metadata["thinkingEnabled"] as? Boolean) ?: false

    /** Provider-profile option keys understood by the Codex CLI adapter. */
    companion object {
        /** Explicit Codex CLI executable path option. */
        const val OPTION_EXECUTABLE_PATH = "codex.executable.path"
    }

    private fun effectiveModel(model: String): String = model.takeIf(String::isNotBlank).orEmpty()

    private fun callbacks(
        request: ChatRequestContext,
        model: String,
    ) = toolCallbacks.functionCallbacks(
        request.enabledTools,
        request.metadata + mapOf("model" to model, "provider" to "codex"),
    )
}
