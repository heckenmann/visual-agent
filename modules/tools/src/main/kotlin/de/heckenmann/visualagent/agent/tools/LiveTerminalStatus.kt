package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult

/** Keeps the live terminal label consistent with the runtime's canonical outcome code. */
internal fun liveTerminalStatus(result: ToolResult?): String =
    when {
        result?.success == true -> "Completed"
        result?.error?.substringBefore(':') in setOf("TOOL_CANCELLED", "CANCELLED") -> "Cancelled"
        result?.error?.substringBefore(':') in setOf("TOOL_TIMEOUT", "TIMEOUT") -> "Timed out"
        else -> "Failed"
    }
