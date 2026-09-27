# UC-0000138: Inspect Server Environment Variables

## Goal

Allow an explicitly authorized agent to inspect the actual environment variables of the Visual Agent
server process for troubleshooting.

## Primary Actor

The main agent, or a specifically configured sub-agent, trusted to receive environment values that
may contain credentials or other sensitive data.

## Preconditions

- The user explicitly enables `system:env` in global tool settings. It is disabled by default and
  excluded from all default agent profiles.
- The requesting agent is configured to receive the tool.
- The user understands that returned values can include API keys, tokens, passwords, and personal
  information.

## Main Flow

1. The agent calls `system:env` with `list`, `search`, or `get`.
2. The tool reads the current Visual Agent server process environment using the JDK `System.getenv()`
   API; it does not read or modify another process environment.
3. `list` and `search` return a bounded page of variable names and actual value chunks. `search`
   matches variable names only, case-insensitively.
4. `get` returns an actual value chunk for one exact variable name. The agent advances to the returned
   `nextValueOffset` until `hasMoreValue` is false. Offsets and limits count Unicode code points.
5. Results are returned to the model without redaction. A tool result is part of the conversation
   exchange and may be persisted in conversation history; it is not written to general application
   logs or automatically added to context.

## Result

The tool reports server-process environment only. It never mutates the process environment or invokes
a shell. Result values are deliberately unredacted after the user enables the tool and the agent
explicitly calls it. Do not enable this tool for an agent unless the model and conversation-history
storage are trusted with every requested value.

The implementation uses Java SE `System.getenv()` directly. The JDK provides both the complete
read-only environment map and exact-name value lookup; a separate library would add no capability.

## Tool Calls

- `system:env`: first page: `{"action":"list","offset":0,"pageSize":10,"valueLimit":1024}`.
- `system:env`: search variable names: `{"action":"search","query":"PATH","offset":0,"pageSize":10}`.
- `system:env`: get a complete value in bounded chunks: `{"action":"get","name":"PATH","valueOffset":0,"valueLimit":4096}`; continue at the returned `nextValueOffset` until `hasMoreValue` is false.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemEnvironmentTool`
- `de.heckenmann.visualagent.agent.tools.EnvironmentVariablesProvider`
- `de.heckenmann.visualagent.agent.tools.JvmEnvironmentVariablesProvider`
- `de.heckenmann.visualagent.agent.config.AgentToolConfigService`

## Acceptance Criteria

- The tool is globally disabled by default and is absent from default agent profiles.
- Exact values, including secrets, are returned only after global enablement and an explicit tool call.
- List/search pagination and get-value chunking bound every result, preserve Unicode code points, and allow complete values to be retrieved.
- Search matches names only; all returned variable values remain unredacted.
- Environment inspection is server-process-only, read-only, and does not invoke a shell.
- Values are never written to general application logs or automatically injected into system context.
- Tests use an injected deterministic environment snapshot and do not depend on host-specific variables.

## References

- [Oracle Java SE tutorial: Environment Variables](https://docs.oracle.com/javase/tutorial/essential/environment/env.html)
- [Oracle Java SE 24 API: `System.getenv`](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/System.html#getenv())
