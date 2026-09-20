package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ProviderFinishReason
import de.heckenmann.visualagent.agent.ProviderResponseMetadata
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingError
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import de.heckenmann.visualagent.agent.provider.ProviderWorkingDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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

    override fun chatReactive(messages: List<Message>): Mono<ChatResponse> =
        Mono.error(IllegalStateException("Codex CLI chat requires a configured provider profile"))

    override fun chatReactive(request: ChatRequestContext): Mono<ChatResponse> =
        mono {
            val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
            val model = effectiveModel(request.model ?: profile.defaultModel)
            val executable = withContext(Dispatchers.IO) { resolveExecutable(profile) }
            val chatModel =
                CodexAppServerChatModel(
                    executable,
                    model,
                    callbacks(request, model),
                    request.workingDirectory(),
                    request.showReasoningSummary(),
                )
            val response =
                chatModel
                    .completeReactive(request.toPrompt(toolCallbacks.toolRuntimeGuidance()), request.cancellationToken)
                    .awaitSingle()
            ChatResponse(
                model = response.metadata.model.takeIf(String::isNotBlank) ?: model,
                message = response.toCodexProviderMessage(),
                done = true,
                providerTurn = response.toCodexProviderTurn(model),
            )
        }

    override fun streamReactive(messages: List<Message>): Flux<ChatResponse> =
        Flux.error(IllegalStateException("Codex CLI streaming requires a configured provider profile"))

    override fun streamReactive(request: ChatRequestContext): Flux<ChatResponse> =
        mono {
            val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
            val model = effectiveModel(request.model ?: profile.defaultModel)
            val executable = withContext(Dispatchers.IO) { resolveExecutable(profile) }
            ResolvedCodexRequest(executable, model)
        }.flatMapMany { resolved ->
            CodexAppServerChatModel(
                resolved.executable,
                resolved.model,
                callbacks(request, resolved.model),
                request.workingDirectory(),
                request.showReasoningSummary(),
            ).streamReactive(request.toPrompt(toolCallbacks.toolRuntimeGuidance()), request.cancellationToken)
                .map { chunk ->
                    ChatResponse(
                        model = chunk.metadata.model.takeIf(String::isNotBlank) ?: resolved.model,
                        message = chunk.toCodexProviderMessage(),
                        done = chunk.hasFinishReasons(setOf("stop")),
                        providerTurn = chunk.toCodexProviderTurn(resolved.model),
                    )
                }
        }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
    ): Mono<ChatResponse> = Mono.error(IllegalStateException("Codex CLI vision requires a configured provider profile"))

    /** Sends an image through the configured Codex app-server profile. */
    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String,
        profile: ProviderProfile,
    ): Mono<ChatResponse> =
        mono {
            val model = effectiveModel(modelId.ifBlank { profile.defaultModel })
            val executable = withContext(Dispatchers.IO) { resolveExecutable(profile) }
            val response =
                CodexAppServerChatModel(
                    executable,
                    model,
                    emptyList(),
                    workingDirectory.get(),
                ).completeVisionReactive(image, prompt).awaitSingle()
            ChatResponse(
                model = response.metadata.model.takeIf(String::isNotBlank) ?: model,
                message = response.toCodexProviderMessage(),
                done = true,
                providerTurn = response.toCodexProviderTurn(model),
            )
        }

    override fun embeddingsReactive(text: String): Mono<List<Double>> = Mono.just(emptyList())

    override fun isConnected(): Boolean = true

    override fun checkConnectionReactive(): Mono<Boolean> = Mono.just(false)

    override fun getModelsReactive(): Mono<List<String>> =
        Mono.error(IllegalStateException("Codex CLI model discovery is provided by the Codex model catalog"))

    override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
        Mono.just(ShowResponse(model = modelName, modifiedAt = "", details = ModelDetails(family = "Codex CLI")))

    override fun getModelDetailsReactive(
        profile: ProviderProfile,
        modelName: String,
    ): Mono<ShowResponse> = getModelDetailsReactive(modelName)

    override fun loadModelsReactive(profile: ProviderProfile): Mono<List<ProviderModelConfig>> = modelCatalog.loadReactive(profile)

    private suspend fun resolveExecutable(profile: ProviderProfile): Path =
        when (val result = locator.locate(profile.options[OPTION_EXECUTABLE_PATH])) {
            is CodexCliLocation.Ready -> result.executable
            CodexCliLocation.InvalidExplicitPath ->
                throw providerExecutableUnavailable("The configured provider executable path is invalid.")
            CodexCliLocation.Missing ->
                throw providerExecutableUnavailable("The required provider executable is not installed.")
        }

    private fun providerExecutableUnavailable(reason: String): ProviderUserFacingException =
        ProviderUserFacingException(
            ProviderUserFacingError(
                summary = "Provider executable unavailable",
                detail = "$reason Install it or select another provider in Session settings.",
                retryable = false,
            ),
        )

    private fun ChatRequestContext.toPrompt(toolRuntimeGuidance: String): Prompt =
        Prompt(
            (listOf(Message("system", "Tool timeout contract: $toolRuntimeGuidance")) + messages).map { message ->
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
        request.metadata + mapOf("model" to model, "provider" to "codex") +
            (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
    )

    private data class ResolvedCodexRequest(
        val executable: Path,
        val model: String,
    )
}

/**
 * Carries the Codex assistant item identifier across the provider-neutral boundary.
 *
 * @return Provider-neutral assistant message with optional Codex metadata
 */
internal fun org.springframework.ai.chat.model.ChatResponse.toCodexProviderMessage(): Message {
    val itemId = metadata.get<String>("codexItemId")
    val messageMetadata =
        itemId?.let {
            buildJsonObject {
                put("codexItemId", it)
            }.toString()
        }
    return Message(
        role = "assistant",
        content = result?.output?.text.orEmpty(),
        metadata = messageMetadata,
    )
}

/** Maps allowlisted Codex app-server output to a provider-neutral model turn. */
internal fun org.springframework.ai.chat.model.ChatResponse.toCodexProviderTurn(fallbackModel: String): ProviderTurnResponse {
    val rawFinishReason = result?.metadata?.finishReason
    return ProviderTurnResponse(
        model = metadata.model.takeIf(String::isNotBlank) ?: fallbackModel,
        content = result?.output?.text.orEmpty(),
        reasoning = metadata.get<String>("codexReasoning"),
        reasoningIsSummary = true,
        finishReason = rawFinishReason?.let { ProviderFinishReason.STOP },
        metadata =
            ProviderResponseMetadata(
                responseId = metadata.get<String>("codexItemId"),
                rawFinishReason = rawFinishReason,
            ),
    )
}
