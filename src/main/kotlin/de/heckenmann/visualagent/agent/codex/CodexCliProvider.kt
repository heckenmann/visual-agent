package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import io.github.vupoint.cokit.client.CodexCursor
import io.github.vupoint.cokit.client.CodexRpc
import io.github.vupoint.cokit.client.models.ModelListParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Codex CLI subscription provider backed exclusively by CoKit and `codex app-server`. */
@Component
class CodexCliProvider internal constructor(
    private val locator: CodexCliLocator,
    private val connectionFactory: CodexAppServerConnector,
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = error("Codex CLI chat requires a configured provider profile")

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
            val modelName = request.model ?: profile.defaultModel
            val chatModel = chatModel(profile, modelName, request.workingDirectory(), request.cancellationToken)
            val response = chatModel.call(request.toPrompt())
            ChatResponse(
                model = response.metadata.model.ifBlank { modelName },
                message = Message("assistant", requireNotNull(response.result).output.text.orEmpty()),
                done = true,
            )
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> =
        error("Codex CLI streaming requires a configured provider profile")

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val profile = requireNotNull(request.providerProfile) { "Codex CLI provider profile is missing" }
        val modelName = request.model ?: profile.defaultModel
        val chatModel = chatModel(profile, modelName, request.workingDirectory(), request.cancellationToken)
        return flow {
            chatModel.stream(request.toPrompt()).asFlow().collect { response ->
                val generation = requireNotNull(response.result)
                emit(
                    ChatResponse(
                        model = response.metadata.model.ifBlank { modelName },
                        message = Message("assistant", generation.output.text.orEmpty()),
                        done = generation.metadata.finishReason != null,
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = error("Codex CLI vision is not supported by this provider adapter")

    override suspend fun embeddings(text: String): List<Double> = emptyList()

    override fun isConnected(): Boolean = true

    override suspend fun checkConnection(): Boolean = false

    override suspend fun getModels(): List<String> = error("Codex CLI model discovery requires a provider profile")

    internal suspend fun getModels(profile: ProviderProfile): List<String> =
        withContext(Dispatchers.IO) {
            val executable = resolveExecutable(profile)
            val models = linkedMapOf<String, String>()
            var cursor: CodexCursor? = null
            connectionFactory.connect(executable, defaultWorkingDirectory()).use { connection ->
                do {
                    val page =
                        connection.client.request(
                            CodexRpc.Model.List,
                            ModelListParams(cursor = cursor, includeHidden = true, limit = MODEL_PAGE_SIZE),
                        )
                    page.data.forEach { models[it.model.value] = it.displayName }
                    cursor = page.nextCursor
                } while (cursor != null)
            }
            check(models.isNotEmpty()) { "Codex CLI returned no available models" }
            models.keys.toList()
        }

    override suspend fun getModelDetails(modelName: String): ShowResponse =
        ShowResponse(model = modelName, modifiedAt = "", details = ModelDetails(family = "Codex CLI"))

    internal suspend fun getModelDetails(
        profile: ProviderProfile,
        modelName: String,
    ): ShowResponse = getModelDetails(modelName)

    private suspend fun chatModel(
        profile: ProviderProfile,
        model: String,
        workingDirectory: Path,
        cancellationToken: de.heckenmann.visualagent.agent.CancellationToken?,
    ): CodexCliChatModel =
        CodexCliChatModel(
            CoKitCodexAppServerChatBridge(connectionFactory, resolveExecutable(profile), workingDirectory, model),
            cancellationToken,
        )

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
            ?: defaultWorkingDirectory()

    private fun defaultWorkingDirectory(): Path = WorkspaceFilePaths.workspaceRoot()

    internal companion object {
        const val OPTION_EXECUTABLE_PATH = "codex.executable.path"
        private const val MODEL_PAGE_SIZE = 100
    }
}
