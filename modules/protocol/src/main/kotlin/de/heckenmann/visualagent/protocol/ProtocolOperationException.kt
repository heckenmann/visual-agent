package de.heckenmann.visualagent.protocol

/**
 * Exception carrying an operation error that is safe for a presentation client to render.
 *
 * The original cause is retained for server-side diagnostics, while clients use [error] and
 * never need to inspect the raw exception message.
 */
class ProtocolOperationException(
    val error: UserFacingError,
    cause: Throwable? = null,
) : RuntimeException("${error.summary}: ${error.detail}", cause)
