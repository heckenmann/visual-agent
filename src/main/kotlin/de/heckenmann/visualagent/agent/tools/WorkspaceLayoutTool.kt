package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/**
 * Tool that lets agents inspect and arrange the internal workspace windows.
 */
@Component
class WorkspaceLayoutTool(
    private val workspaceLayoutService: WorkspaceLayoutService,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("workspace:layout"),
            name = ToolId("workspace:layout").toFunctionName(),
            description =
                "Inspect or arrange internal UI panels in the horizontal workspace row. Actions: get, set. " +
                    "get returns screens, main window size, desktop size, and all panels with order, visible, and preferredWidth. " +
                    "set input: {\"action\":\"set\",\"windows\":[{\"id\":\"chat\",\"order\":0,\"visible\":true,\"preferredWidth\":640}]}",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return when (input.string("action") ?: "get") {
            "get" -> success("workspace:layout", json.encodeToString(workspaceLayoutService.report()))
            "set" -> setLayout(input)
            else -> failure("workspace:layout", "Unsupported workspace layout action")
        }
    }

    private fun setLayout(input: JsonObject): ToolResult {
        val patches = input["windows"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        if (patches.isEmpty()) return failure("workspace:layout", "Missing windows array")
        val current = workspaceLayoutService.report().windows.associateBy(WorkspaceWindowState::id)
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
                .sortedBy(WorkspaceWindowState::order)
        val applied = workspaceLayoutService.applyWindowStates(merged)
        return success("workspace:layout", json.encodeToString(applied))
    }

    private fun JsonObject.toWindowState(existing: WorkspaceWindowState?): WorkspaceWindowState =
        WorkspaceWindowState(
            id = requiredString("id"),
            order = int("order") ?: existing?.order ?: 0,
            visible = boolean("visible") ?: existing?.visible ?: true,
            preferredWidth = double("preferredWidth") ?: existing?.preferredWidth ?: 0.0,
        )

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private companion object {
        val json =
            Json {
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
