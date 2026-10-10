package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Reports the current Visual Agent server time in UTC and the server or requested time zone. */
@AgentTool
class ServerTimeTool(
    private val clock: Clock,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            batchSafety = de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety.READ_ONLY_PARALLEL,
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Return the current Visual Agent server time, including UTC, server-local time, timezone, and epoch milliseconds. " +
                    "Optionally pass a Java timezone such as Europe/Berlin. Input: {\"zoneId\":\"Europe/Berlin\"}.",
            inputSchema = """{"type":"object","properties":{"zoneId":{"type":"string"}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val requestedZone =
            parseObject(inputJson).string("zoneId")?.let { zoneId ->
                runCatching { ZoneId.of(zoneId) }
                    .getOrElse { return failure(TOOL_ID.value, "Invalid zoneId. Use a valid Java timezone such as Europe/Berlin.") }
            }
        val instant = clock.instant()
        val serverTime = instant.atZone(clock.zone)
        val data =
            buildJsonObject {
                put("serverInstantUtc", DateTimeFormatter.ISO_INSTANT.format(instant))
                put("serverLocalTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(serverTime))
                put("serverZoneId", clock.zone.id)
                put("serverUtcOffset", serverTime.offset.id)
                put("epochMilliseconds", instant.toEpochMilli())
                requestedZone?.let { zone ->
                    put("requestedZoneId", zone.id)
                    put("requestedLocalTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zone)))
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:time")
    }
}
