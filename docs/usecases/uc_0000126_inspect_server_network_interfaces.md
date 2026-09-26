# UC-0000126: Inspect Server Network Interfaces

## Goal

Let an enabled agent inspect network interface state and assigned IP addresses on the Visual Agent
server.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:interfaces` tool is enabled for the requesting agent.
- The Visual Agent server JVM can enumerate the host's network interfaces.

## Main Flow

1. The agent requests the current interface snapshot with an empty input object.
2. The server reads interface names, operational flags, MTU, and assigned numeric addresses through
   `java.net.NetworkInterface`.
3. The server sorts results and caps output at 64 interfaces and 256 addresses.
4. Hardware addresses and unrelated host details are omitted.

## Result

The agent receives a bounded snapshot of server-side interfaces. It describes the host running the
Visual Agent server, not a remote desktop client.

## Tool Calls

- `network:interfaces`: inspect the current server interfaces with `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkInterfacesTool`
- `de.heckenmann.visualagent.agent.tools.JvmNetworkInterfaceSnapshotProvider`
- `de.heckenmann.visualagent.agent.tools.NetworkInterfaceSnapshotProvider`

## Acceptance Criteria

- Interface enumeration uses only the JDK and adds no runtime dependency.
- The output includes stable interface flags, optional MTU, and numeric addresses.
- The result is sorted and capped; MAC/hardware addresses and unrelated host data are not exposed.
- Tests use deterministic provider fixtures and do not depend on the machine's network configuration.
