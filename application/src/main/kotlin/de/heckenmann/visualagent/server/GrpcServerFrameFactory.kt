package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.protocol.v1.ChatCompleted
import de.heckenmann.visualagent.protocol.v1.ChatDelta
import de.heckenmann.visualagent.protocol.v1.ClientDataRequest
import de.heckenmann.visualagent.protocol.v1.HelloAck
import de.heckenmann.visualagent.protocol.v1.OperationError
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import de.heckenmann.visualagent.protocol.v1.Snapshot

/** Creates bounded server-to-client frames for the bidirectional session protocol. */
internal object GrpcServerFrameFactory {
    /** Returns the successful handshake response and its initial readiness snapshot. */
    fun ready(
        sessionId: String,
        revision: Long,
        protocolVersion: String,
    ): ServerFrame =
        ServerFrame
            .newBuilder()
            .setSessionId(sessionId)
            .setServerRevision(revision)
            .setHelloAck(
                HelloAck
                    .newBuilder()
                    .setProtocolVersion(protocolVersion)
                    .setServerName("visual-agent-server")
                    .setServerVersion("unknown")
                    .build(),
            ).setSnapshot(
                Snapshot
                    .newBuilder()
                    .setRevision(revision)
                    .setJson("{\"ready\":true}")
                    .build(),
            ).build()

    /** Returns a correlated request for one bounded client-owned payload. */
    fun clientDataRequest(
        sessionId: String,
        requestId: String,
        revision: Long,
        dataRequestId: String,
        purpose: String,
        maximumBytes: Long,
        acceptedContentType: String,
    ): ServerFrame =
        ServerFrame
            .newBuilder()
            .setSessionId(sessionId)
            .setRequestId(requestId)
            .setServerRevision(revision)
            .setClientDataRequest(
                ClientDataRequest
                    .newBuilder()
                    .setDataRequestId(dataRequestId)
                    .setPurpose(purpose)
                    .setMaxPayloadBytes(maximumBytes)
                    .addAcceptedContentTypes(acceptedContentType)
                    .build(),
            ).build()

    /** Returns a provider-stream update associated with its request and assistant turn. */
    fun chatDelta(
        sessionId: String,
        requestId: String,
        revision: Long,
        update: ConversationStreamUpdate,
    ): ServerFrame =
        ServerFrame
            .newBuilder()
            .setSessionId(sessionId)
            .setRequestId(requestId)
            .setServerRevision(revision)
            .setChatDelta(
                ChatDelta
                    .newBuilder()
                    .setText(update.textDelta)
                    .setAssistantTurnId(update.assistantTurnId)
                    .setContextReduced(update.contextReduced)
                    .build(),
            ).build()

    /** Returns the successful terminal frame for one completed chat request. */
    fun chatCompleted(
        sessionId: String,
        requestId: String,
        revision: Long,
    ): ServerFrame =
        ServerFrame
            .newBuilder()
            .setSessionId(sessionId)
            .setRequestId(requestId)
            .setServerRevision(revision)
            .setChatCompleted(ChatCompleted.newBuilder().setSuccessful(true).build())
            .build()

    /** Returns a provider-neutral error frame without raw exception data. */
    fun error(
        sessionId: String,
        requestId: String,
        revision: Long,
        code: String,
        message: String,
        retryable: Boolean,
    ): ServerFrame =
        ServerFrame
            .newBuilder()
            .setSessionId(sessionId)
            .setRequestId(requestId)
            .setServerRevision(revision)
            .setError(
                OperationError
                    .newBuilder()
                    .setCode(code)
                    .setMessage(message)
                    .setRetryable(retryable)
                    .build(),
            ).build()
}
