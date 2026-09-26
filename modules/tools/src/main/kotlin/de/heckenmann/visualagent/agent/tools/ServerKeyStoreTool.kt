package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.stereotype.Component

/** Inspects and generates credentials in the Visual Agent managed server key store. */
@AgentTool
@Component
class ServerKeyStoreTool(
    private val tlsMaterial: ServerTlsMaterialPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect public metadata, generate a self-signed CA or TLS server key pair/certificate, remove an exact alias, " +
                    "or export a public certificate from the Visual Agent server's managed PKCS#12 key store. " +
                    "Private keys and passwords are never returned. Changes to the remote gRPC certificate require a server restart. " +
                    "Server certificates require DNS/IP SANs and a managed CA signing alias. Input: {\"action\":\"list|inspect|generateCertificate|removeEntry|exportCertificate\",\"alias\":\"server\",\"subject\":\"CN=agent.example\",\"dnsNames\":[\"agent.example\"],\"ipAddresses\":[],\"certificateAuthority\":false,\"validityDays\":365,\"signingCaAlias\":\"root-ca\"}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","inspect","generateCertificate","removeEntry","exportCertificate"]},"alias":{"type":"string","maxLength":64},"subject":{"type":"string","maxLength":512},"dnsNames":{"type":"array","items":{"type":"string","maxLength":253},"maxItems":20},"ipAddresses":{"type":"array","items":{"type":"string","maxLength":45},"maxItems":20},"certificateAuthority":{"type":"boolean"},"validityDays":{"type":"integer","minimum":1,"maximum":825},"signingCaAlias":{"type":"string","maxLength":64}},"required":["action"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return runCatching {
            when (input.string("action")) {
                "list" -> entriesResult(tlsMaterial.list(ServerTlsStore.KEY))
                "inspect" -> {
                    val entry =
                        tlsMaterial.inspect(ServerTlsStore.KEY, input.requiredString("alias"), true)
                            ?: return failure(TOOL_ID.value, "No key-store entry exists for that alias.")
                    entriesResult(listOf(entry))
                }
                "generateCertificate" -> generate(input)
                "removeEntry" -> {
                    if (!tlsMaterial.removeKeyEntry(input.requiredString("alias"))) {
                        return failure(TOOL_ID.value, "No key-store entry exists for that alias.")
                    }
                    booleanResult("removed", true, "restartRequired", true)
                }
                "exportCertificate" -> {
                    val pem =
                        tlsMaterial.exportPublicCertificate(input.requiredString("alias"))
                            ?: return failure(TOOL_ID.value, "No public certificate exists for that alias.")
                    stringResult("pem", pem)
                }
                else -> return failure(
                    TOOL_ID.value,
                    "Invalid action. Use list, inspect, generateCertificate, removeEntry, or exportCertificate.",
                )
            }
        }.getOrElse { error ->
            failure(TOOL_ID.value, error.message?.takeIf { error is IllegalArgumentException } ?: "Key-store operation failed.")
        }
    }

    private fun generate(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val alias = input.requiredString("alias")
        val subject = input.requiredString("subject")
        val ca = input.boolean("certificateAuthority") ?: false
        val signingCaAlias = input.string("signingCaAlias")
        val dnsNames = input.stringArray("dnsNames")
        val ipAddresses = input.stringArray("ipAddresses")
        val validityDays = input.int("validityDays") ?: DEFAULT_VALIDITY_DAYS
        val generated = tlsMaterial.generateCertificate(alias, subject, dnsNames, ipAddresses, ca, validityDays, signingCaAlias)
        val data =
            buildJsonObject {
                put("alias", generated.alias)
                put("certificateAuthority", generated.certificateAuthority)
                put("restartRequired", generated.restartRequired)
                put("entry", ServerTlsEntry(generated.alias, "private_key", listOf(generated.certificate)).toJson())
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun entriesResult(entries: List<ServerTlsEntry>): ToolResult {
        val data =
            buildJsonObject {
                put("store", "visual-agent-managed-key")
                putJsonArray("entries") { entries.forEach { add(it.toJson()) } }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun booleanResult(
        key: String,
        value: Boolean,
        secondKey: String,
        secondValue: Boolean,
    ): ToolResult {
        val data =
            buildJsonObject {
                put(key, value)
                put(secondKey, secondValue)
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun stringResult(
        key: String,
        value: String,
    ): ToolResult {
        val data = buildJsonObject { put(key, value) }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("security:keystore")
        const val DEFAULT_VALIDITY_DAYS = 365
    }
}

private fun kotlinx.serialization.json.JsonObject.stringArray(key: String): List<String> {
    val value = this[key] ?: return emptyList()
    return runCatching { value.jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" } }
        .getOrElse { throw IllegalArgumentException("Field '$key' must be an array of strings.") }
}
