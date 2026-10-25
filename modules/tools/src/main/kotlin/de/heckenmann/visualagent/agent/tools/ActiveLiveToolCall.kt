package de.heckenmann.visualagent.agent.tools

/** Invocation-owned state retained only until the underlying execution terminates. */
internal data class ActiveLiveToolCall(
    val token: ToolCancellationToken,
    val started: ToolCallEvent,
    val session: ToolLiveSession,
) {
    /** Returns a start snapshot without exposing client request handles or raw input credentials. */
    fun snapshot(): ToolCallEvent =
        started.copy(
            liveState = session.snapshot(),
            inputJson = sanitizeToolInputForEvent(started.inputJson, started.toolId),
            context = started.context - ClientDataRequester.METADATA_KEY,
        )
}
