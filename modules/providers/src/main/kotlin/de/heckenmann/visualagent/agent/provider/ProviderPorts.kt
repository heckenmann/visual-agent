package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.agent.ToolId
import org.springframework.ai.tool.ToolCallback
import java.nio.file.Path

/** String value whose contents must never be exposed by provider infrastructure. */
typealias ProviderCredential = kotlin.String

/**
 * Runtime settings required by provider implementations.
 *
 * The application owns persistence and mutable settings; provider implementations
 * consume only this narrow view.
 */
interface ProviderRuntimeConfig {
    /** Active provider profile identifier. */
    var llmProvider: String

    /** Default Ollama endpoint. */
    var ollamaLocalUrl: String

    /** Default Ollama model. */
    var ollamaModel: String

    /** Optional Ollama bearer token. */
    var ollamaApiKey: ProviderCredential

    /** Optional OpenAI-compatible API key. */
    var openAiApiKey: ProviderCredential

    /** Default OpenAI-compatible endpoint. */
    var openAiBaseUrl: String

    /** Default OpenAI-compatible model. */
    var openAiModel: String

    /** Provider request timeout in seconds. */
    var timeoutSeconds: Int

    /** Returns the normalized legacy provider identifier. */
    fun normalizedProvider(): String
}

internal class DefaultProviderRuntimeConfig : ProviderRuntimeConfig {
    override var llmProvider = "ollama"
    override var ollamaLocalUrl = "http://localhost:11434"
    override var ollamaModel = ""
    override var ollamaApiKey: ProviderCredential = ""
    override var openAiApiKey: ProviderCredential = ""
    override var openAiBaseUrl = "https://api.openai.com"
    override var openAiModel = ""
    override var timeoutSeconds = 120

    override fun normalizedProvider(): String = if (llmProvider.equals("openai", ignoreCase = true)) "openai" else "ollama"
}

/** Persistence port used by the provider catalog. */
interface ProviderPreferenceStore {
    /** Returns a stored preference or null when absent. */
    fun getPreference(key: String): String?

    /** Stores or replaces one preference value. */
    fun setPreference(
        key: String,
        value: String,
    )
}

/** Resolves application tools into provider-facing Spring AI callbacks. */
interface ProviderToolCallbacks {
    /**
     * Builds callbacks for the enabled application tool identifiers.
     *
     * @param enabledTools Tool identifiers exposed for this request
     * @param context Request-scoped execution metadata
     * @return Provider-facing callbacks
     */
    fun functionCallbacks(
        enabledTools: Set<ToolId>,
        context: Map<String, Any> = emptyMap(),
    ): List<ToolCallback>
}

/** Supplies the default filesystem location used by local provider processes. */
fun interface ProviderWorkingDirectory {
    /** Returns the normalized working directory for a provider process. */
    fun get(): Path
}
