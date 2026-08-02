package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/** Supplies the latest official Codex CLI release tag. */
internal fun interface CodexCliReleaseVersionSource {
    /** Returns the current official release tag, or null when it cannot be determined. */
    suspend fun latestVersionTag(): String?
}

/** Reads the latest official Codex CLI version from the npm registry through npm or Yarn. */
@Component
internal class PackageManagerCodexCliReleaseVersionSource(
    private val processFactory: CodexCliProcessFactory,
) : CodexCliReleaseVersionSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestVersionTag(): String? =
        withContext(Dispatchers.IO) {
            queryPackageManager(NPM_COMMAND, listOf("view", PACKAGE_NAME, "version", "--json"))
                ?: queryPackageManager(YARN_COMMAND, listOf("info", PACKAGE_NAME, "version", "--json"))
        }

    private suspend fun queryPackageManager(
        executable: String,
        arguments: List<String>,
    ): String? =
        runCatching {
            val result = processFactory.run(listOf(executable) + arguments, timeoutSeconds = QUERY_TIMEOUT_SECONDS)
            if (result.timedOut || result.exitCode != 0 || result.stdout.truncated) return@runCatching null
            versionFromJson(result.stdout.text)
        }.getOrNull()

    internal fun versionFromJson(output: String): String? =
        runCatching {
            when (val element = json.parseToJsonElement(output)) {
                is JsonPrimitive -> element.content
                is JsonArray -> element.firstOrNull()?.jsonPrimitive?.content
                is JsonObject -> element["data"]?.jsonPrimitive?.content
            }.trimToNull()
        }.getOrNull()

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf(String::isNotBlank)

    private companion object {
        private const val NPM_COMMAND = "npm"
        private const val YARN_COMMAND = "yarn"
        private const val PACKAGE_NAME = "@openai/codex"
        private const val QUERY_TIMEOUT_SECONDS = 15L
    }
}
