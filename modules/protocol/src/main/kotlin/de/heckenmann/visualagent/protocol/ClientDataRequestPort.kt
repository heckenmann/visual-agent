package de.heckenmann.visualagent.protocol

/** Maximum UTF-8 size accepted for one user-authored conversation message. */
const val MAX_CONVERSATION_TEXT_BYTES = 1_048_576L

/**
 * Request-scoped access to client-owned data. Implementations must collect or transfer each value
 * only when the server explicitly invokes the corresponding method. User-submitted chat and
 * imports are sent directly with their explicit requests; ambient client context is not attached.
 */
interface ClientDataRequestPort {
    /** Requests the desktop client's current JVM snapshot from the client. */
    fun requestRuntimeSnapshot(): ClientRuntimeSnapshot? = null

    /** Requests only the specified bounded page from the desktop client's process inventory. */
    fun requestProcessInventory(request: ClientProcessInventoryRequest): ClientProcessInventorySnapshot? = null

    /** Stable request-context key used only while executing the current server-side tool call. */
    companion object {
        const val METADATA_KEY = "clientDataRequester"
    }
}
