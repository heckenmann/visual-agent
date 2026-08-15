package de.heckenmann.visualagent.protocol

/**
 * Client-side lifecycle boundary between the desktop presentation and the application server.
 *
 * Implementations perform the readiness handshake before exposing [application]. The interface
 * deliberately contains no Spring, Compose, or transport-specific types so an in-process or
 * remote implementation can be selected by the desktop host.
 */
interface ApplicationConnection : AutoCloseable {
    /** Returns the protocol port bundle after a successful readiness handshake. */
    val application: ApplicationPort

    /** Performs the connection and readiness handshake. */
    suspend fun awaitReady()
}
