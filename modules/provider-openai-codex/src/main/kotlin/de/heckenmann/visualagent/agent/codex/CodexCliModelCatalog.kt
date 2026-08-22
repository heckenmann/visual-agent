package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderWorkingDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/** Loads the live model catalog for one configured Codex provider profile. */
fun interface CodexModelCatalog {
    /** Returns model identifiers and display names that are currently selectable. */
    suspend fun load(profile: ProviderProfile): List<ProviderModelConfig>
}

/** Loads the live model catalog exposed by the installed Codex CLI. */
@Component
internal class CodexCliModelCatalog(
    private val locator: CodexCliLocator,
    private val processFactory: CodexCliProcessFactory,
    private val workingDirectory: ProviderWorkingDirectory,
) : CodexModelCatalog {
    /** Returns the selectable models reported by `codex debug models`. */
    override suspend fun load(profile: ProviderProfile): List<ProviderModelConfig> {
        val executable =
            when (val result = locator.locate(profile.options[CodexCliProvider.OPTION_EXECUTABLE_PATH])) {
                is CodexCliLocation.Ready -> result.executable
                CodexCliLocation.InvalidExplicitPath -> error("Configured Codex CLI path is invalid")
                CodexCliLocation.Missing -> error("Codex CLI is not installed")
            }
        val result =
            processFactory.run(
                command = listOf(executable.toString(), "debug", "models"),
                workingDirectory = workingDirectory.get(),
                timeoutSeconds = MODEL_CATALOG_TIMEOUT_SECONDS,
                maxOutputCharacters = MAX_CATALOG_CHARACTERS,
            )
        check(!result.timedOut && result.exitCode == 0 && !result.stdout.truncated) { "Codex model catalog is unavailable" }
        return parse(result.stdout.text)
    }

    internal fun parse(payload: String): List<ProviderModelConfig> =
        Json
            .parseToJsonElement(payload)
            .jsonObject["models"]
            ?.jsonArray
            .orEmpty()
            .asSequence()
            .map { it.jsonObject }
            .filter { it["visibility"]?.jsonPrimitive?.content != "hide" }
            .mapNotNull { model ->
                model["slug"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)?.let { id ->
                    ProviderModelConfig(
                        id = id,
                        name = model["display_name"]?.jsonPrimitive?.content ?: id,
                        capabilities = setOf("vision"),
                    )
                }
            }.distinctBy(ProviderModelConfig::id)
            .toList()

    private companion object {
        private const val MODEL_CATALOG_TIMEOUT_SECONDS = 20L
        private const val MAX_CATALOG_CHARACTERS = 1_000_000
    }
}
