package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolWindowState
import de.heckenmann.visualagent.agent.tools.api.WorkspaceLayoutToolPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool that lets agents inspect and arrange the internal workspace windows.
 */
class WorkspaceLayoutTool(
    private val workspaceLayout: WorkspaceLayoutToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("workspace:layout"),
            name = ToolId("workspace:layout").toFunctionName(),
            description = workspaceLayoutToolDescription(),
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return when (input.string("action") ?: "get") {
            "get" -> success("workspace:layout", workspaceLayout.reportJson())
            "set" -> setLayout(input)
            else -> failure("workspace:layout", "Unsupported workspace layout action")
        }
    }

    private fun setLayout(input: JsonObject): ToolResult {
        val patches = input["windows"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        if (patches.isEmpty()) return failure("workspace:layout", "Missing windows array")
        val current = workspaceLayout.windows().associateBy(ToolWindowState::id)
        val patchedById =
            patches.associate { patch ->
                val id = patch.requiredString("id")
                val existing = current[id]
                id to patch.toWindowState(existing)
            }
        val merged =
            current
                .values
                .map { existing -> patchedById[existing.id] ?: existing }
                .plus(patchedById.filterKeys { it !in current }.values)
                .sortedBy(ToolWindowState::order)
        return success("workspace:layout", workspaceLayout.apply(merged))
    }

    private fun JsonObject.toWindowState(existing: ToolWindowState?): ToolWindowState =
        ToolWindowState(
            id = requiredString("id"),
            order = int("order") ?: existing?.order ?: 0,
            visible = boolean("visible") ?: existing?.visible ?: true,
            preferredWidth = double("preferredWidth") ?: existing?.preferredWidth ?: 0.0,
        )

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private companion object {
        /**
         * Returns the tool description for workspace:layout with all actions and their parameters.
         */
        fun workspaceLayoutToolDescription(): String =
            "Inspect or arrange internal UI panels in the horizontal workspace row. Actions:\n" +
                "- get: {\"action\":\"get\"}. Returns screens, main window size, desktop size, " +
                "and all panels with order, visible, and preferredWidth.\n" +
                "- set: {\"action\":\"set\"," +
                "\"windows\":[{\"id\":\"chat\",\"order\":0,\"visible\":true,\"preferredWidth\":640}]}. " +
                "Panel IDs: chat, todos, files, canvas, agents, settings. Use get first to see current layout."
    }
}
