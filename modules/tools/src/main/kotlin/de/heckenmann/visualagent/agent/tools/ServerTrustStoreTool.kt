package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.stereotype.Component

/** Inspects and changes only the Visual Agent managed additive server trust store. */
@AgentTool
@Component
class ServerTrustStoreTool(
    private val tlsMaterial: ServerTlsMaterialPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect the Visual Agent server's managed trust store, import a CA certificate, or remove one exact alias. " +
                    "Trust changes affect outbound server TLS and may require restart. Never modifies the JVM's built-in cacerts. " +
                    "Certificate input must be bounded ASCII PEM. Input: {\"action\":\"list|inspect|importCertificate|removeCertificate\",\"alias\":\"internal-ca\",\"certificate\":\"-----BEGIN CERTIFICATE-----...\",\"includePem\":false}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","inspect","importCertificate","removeCertificate"]},"alias":{"type":"string","maxLength":64},"certificate":{"type":"string","maxLength":131072},"includePem":{"type":"boolean"}},"required":["action"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return runCatching {
            when (input.string("action")) {
                "list" -> result(tlsMaterial.list(ServerTlsStore.TRUST))
                "inspect" -> {
                    val alias = input.requiredString("alias")
                    val entry =
                        tlsMaterial.inspect(ServerTlsStore.TRUST, alias, input.boolean("includePem") == true)
                            ?: return failure(TOOL_ID.value, "No trust-store certificate exists for that alias.")
                    result(listOf(entry))
                }
                "importCertificate" -> {
                    val alias = input.requiredString("alias")
                    val pem = input.requiredString("certificate")
                    if (pem.length > MAX_CERTIFICATE_CHARS) return failure(TOOL_ID.value, "Certificate input exceeds the 128 KiB limit.")
                    val entry = tlsMaterial.importTrustedCertificate(alias, pem)
                    buildResult(entry, "restartRequired" to true)
                }
                "removeCertificate" -> {
                    val removed = tlsMaterial.removeTrustedCertificate(input.requiredString("alias"))
                    if (!removed) return failure(TOOL_ID.value, "No trust-store certificate exists for that alias.")
                    simpleResult("removed", true, "restartRequired", true)
                }
                else -> return failure(TOOL_ID.value, "Invalid action. Use list, inspect, importCertificate, or removeCertificate.")
            }
        }.getOrElse { error ->
            failure(TOOL_ID.value, error.message?.takeIf { error is IllegalArgumentException } ?: "Trust-store operation failed.")
        }
    }

    private fun result(entries: List<ServerTlsEntry>): ToolResult {
        val data =
            buildJsonObject {
                put("store", "visual-agent-managed-trust")
                putJsonArray("entries") { entries.forEach { add(it.toJson()) } }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun buildResult(
        entry: ServerTlsEntry,
        vararg fields: Pair<String, Boolean>,
    ): ToolResult {
        val data =
            buildJsonObject {
                put("store", "visual-agent-managed-trust")
                put("entry", entry.toJson())
                fields.forEach { (key, value) -> put(key, value) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun simpleResult(
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

    private companion object {
        val TOOL_ID = ToolId("security:truststore")
        const val MAX_CERTIFICATE_CHARS = 131_072
    }
}
