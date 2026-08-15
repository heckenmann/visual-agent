package de.heckenmann.visualagent.protocol

/** Version negotiated by desktop clients and Visual Agent servers. */
object ProtocolVersion {
    /** Current wire contract version. */
    const val CURRENT = "v1"
}

/** Stable operation error categories exposed by the transport boundary. */
enum class ProtocolErrorCode {
    /** The client and server cannot use the same protocol version. */
    INCOMPATIBLE_PROTOCOL,

    /** The server is not ready to handle a request. */
    NOT_READY,

    /** The request was cancelled by the client. */
    CANCELLED,

    /** The connection ended before the operation completed. */
    CONNECTION_LOST,

    /** The operation failed without a retry-safe classification. */
    OPERATION_FAILED,
}

/** User-safe error category rendered by presentation clients. */
enum class ProtocolErrorCategory {
    PROVIDER,
    WORKSPACE,
    CANVAS,
    TOOL,
    PERSISTENCE,
    UNKNOWN,
}

/** Structured operation error independent from Spring and UI implementations. */
data class UserFacingError(
    val category: ProtocolErrorCategory,
    val summary: String,
    val detail: String,
    val retryable: Boolean = false,
)
