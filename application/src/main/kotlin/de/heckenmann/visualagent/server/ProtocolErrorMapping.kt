package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.UserFacingError
import kotlin.coroutines.cancellation.CancellationException

/** Converts application failures into safe protocol exceptions at the server boundary. */
internal fun Throwable.toProtocolOperationException(): ProtocolOperationException {
    if (this is ProtocolOperationException) return this
    val mapped = ErrorMessageMapper.map(this)
    return ProtocolOperationException(
        error =
            UserFacingError(
                category = ProtocolErrorCategory.valueOf(mapped.category.name),
                summary = mapped.summary,
                detail = mapped.detail,
                retryable = mapped.retryable,
            ),
        cause = this,
    )
}

/** Executes one protocol operation and exposes only a user-safe failure to the client. */
internal inline fun <T> protocolBoundary(block: () -> T): T =
    try {
        block()
    } catch (error: ProtocolOperationException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw error.toProtocolOperationException()
    }
