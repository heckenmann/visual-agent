package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.UpdateAssetInfo
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckPort
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckRequest
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckResult
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Checks the current Visual Agent version against the public GitHub release channel. */
@AgentTool
class UpdateCheckTool(
    private val updates: UpdateCheckPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Check the current Visual Agent version and whether a GitHub release update is available. " +
                    "The default channel is stable; set includePrerelease=true only when preview releases are wanted. " +
                    "This tool detects updates only and never downloads or installs them. " +
                    "Input: {\"includePrerelease\":false,\"packageType\":\"optional-package-id\"}.",
            inputSchema = UPDATE_CHECK_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        runCatching {
            val input = parseObject(inputJson)
            val result =
                updates.check(
                    UpdateCheckRequest(
                        includePrerelease = input.boolean("includePrerelease") ?: false,
                        packageType = input.string("packageType"),
                    ),
                )
            success(TOOL_ID, result.toJson().toString())
        }.getOrElse { error ->
            failure(TOOL_ID, error.message ?: "Update check failed")
        }

    private companion object {
        const val TOOL_ID = "update:check"
        const val UPDATE_CHECK_SCHEMA =
            """{"type":"object","properties":{"includePrerelease":{"type":"boolean"},"packageType":{"type":"string"}},"additionalProperties":false}"""
    }
}

private fun UpdateCheckResult.toJson() =
    buildJsonObject {
        put("currentVersion", currentVersion)
        latestVersion?.let { put("latestVersion", it) }
        releaseTag?.let { put("releaseTag", it) }
        put("updateAvailable", updateAvailable)
        put("channel", channel)
        releaseName?.let { put("releaseName", it) }
        releaseNotes?.let { put("releaseNotes", it) }
        releaseUrl?.let { put("releaseUrl", it) }
        publishedAt?.let { put("publishedAt", it) }
        selectedAsset?.let { put("selectedAsset", it.toJson()) }
        put(
            "availableAssets",
            buildJsonArray { availableAssets.forEach { add(it.toJson()) } },
        )
    }

private fun UpdateAssetInfo.toJson() =
    buildJsonObject {
        put("name", name)
        put("sizeBytes", sizeBytes)
        digest?.let { put("sha256", it) }
    }
