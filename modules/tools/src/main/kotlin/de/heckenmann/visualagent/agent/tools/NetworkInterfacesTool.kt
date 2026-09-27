package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.stereotype.Component
import java.net.NetworkInterface
import java.net.SocketException

/** Lists a bounded, read-only snapshot of network interfaces on the Visual Agent server. */
@AgentTool
class NetworkInterfacesTool(
    private val provider: NetworkInterfaceSnapshotProvider,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "List server-side network interface state, flags, MTU, and assigned IP addresses. " +
                    "Hardware addresses and unrelated host data are omitted. Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val snapshots =
            try {
                provider.snapshot()
            } catch (_: Exception) {
                return failure(TOOL_ID.value, "Network interface information is unavailable on the Visual Agent server.")
            }
        val truncated = snapshots.size > MAX_INTERFACES || snapshots.sumOf { it.addresses.size } > MAX_ADDRESSES
        val data =
            buildJsonObject {
                put("truncated", truncated)
                putJsonArray("interfaces") {
                    var remainingAddresses = MAX_ADDRESSES
                    snapshots
                        .sortedBy(InterfaceSnapshot::name)
                        .take(MAX_INTERFACES)
                        .forEach { snapshot ->
                            val addresses =
                                snapshot.addresses
                                    .distinct()
                                    .sorted()
                                    .take(remainingAddresses)
                            remainingAddresses -= addresses.size
                            add(
                                buildJsonObject {
                                    put("name", snapshot.name)
                                    put("up", snapshot.up)
                                    put("loopback", snapshot.loopback)
                                    put("pointToPoint", snapshot.pointToPoint)
                                    put("multicast", snapshot.multicast)
                                    snapshot.mtu?.let { put("mtu", it) }
                                    putJsonArray("addresses") { addresses.forEach { add(JsonPrimitive(it)) } }
                                },
                            )
                        }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:interfaces")
        const val MAX_INTERFACES = 64
        const val MAX_ADDRESSES = 256
    }
}

/** Data-only snapshot of one network interface. */
data class InterfaceSnapshot(
    /** Stable OS interface name. */
    val name: String,
    /** Whether the interface is administratively up. */
    val up: Boolean,
    /** Whether the interface is a loopback interface. */
    val loopback: Boolean,
    /** Whether the interface is point-to-point. */
    val pointToPoint: Boolean,
    /** Whether the interface supports multicast. */
    val multicast: Boolean,
    /** Maximum transmission unit, when exposed by the platform. */
    val mtu: Int?,
    /** Assigned numeric IP addresses. */
    val addresses: List<String>,
)

/** Provides server-side network interface snapshots. */
fun interface NetworkInterfaceSnapshotProvider {
    /** Return available interfaces and their assigned IP addresses. */
    fun snapshot(): List<InterfaceSnapshot>
}

/** Reads network interfaces with the JDK network-interface API. */
@Component
class JvmNetworkInterfaceSnapshotProvider : NetworkInterfaceSnapshotProvider {
    override fun snapshot(): List<InterfaceSnapshot> =
        try {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            val interfaces =
                buildList {
                    enumeration?.let { values ->
                        while (values.hasMoreElements()) add(values.nextElement())
                    }
                }
            interfaces.mapNotNull(::snapshot)
        } catch (error: SocketException) {
            throw IllegalStateException("Could not enumerate server network interfaces", error)
        }

    private fun snapshot(networkInterface: NetworkInterface): InterfaceSnapshot? =
        runCatching {
            InterfaceSnapshot(
                name = networkInterface.name,
                up = networkInterface.isUp,
                loopback = networkInterface.isLoopback,
                pointToPoint = networkInterface.isPointToPoint,
                multicast = networkInterface.supportsMulticast(),
                mtu = networkInterface.mtu.takeIf { it >= 0 },
                addresses =
                    networkInterface.inetAddresses
                        .toList()
                        .map { it.hostAddress }
                        .distinct()
                        .sorted(),
            )
        }.getOrNull()
}
