package de.heckenmann.visualagent.agent

import java.util.UUID

/** Creates stable, request-scoped identities for ordered assistant turns. */
object AssistantTurnIdentity {
    /** Returns an identity for tool turns reported independently of streamed assistant text rounds. */
    fun forProviderToolTurn(
        requestId: String,
        providerTurnId: String,
    ): String = UUID.nameUUIDFromBytes("$requestId:provider-tool-turn:$providerTurnId".toByteArray()).toString()

    /** Returns the request ID for round zero and a deterministic UUID for later rounds. */
    fun forRound(
        requestId: String,
        round: Int,
    ): String {
        require(round >= 0) { "Assistant turn round must not be negative" }
        if (round == 0) return requestId
        return UUID.nameUUIDFromBytes("$requestId:assistant-turn:$round".toByteArray()).toString()
    }
}
