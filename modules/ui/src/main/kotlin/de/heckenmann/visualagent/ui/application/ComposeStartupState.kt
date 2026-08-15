package de.heckenmann.visualagent.ui.application

/**
 * Coarse lifecycle phases shown while the desktop host establishes its server connection.
 */
enum class StartupPhase {
    /** Compose has rendered, but the server bootstrap has not started yet. */
    STARTING_UI,

    /** The desktop host is resolving its workstation-local endpoint configuration. */
    RESOLVING_ENDPOINT,

    /** The local Spring server is being started. */
    STARTING_SERVER,

    /** The desktop host is connecting to a configured remote server. */
    CONNECTING_REMOTE,

    /** Spring is ready and application services are being resolved. */
    LOADING_RUNTIME,

    /** The client and server are negotiating protocol readiness. */
    HANDSHAKING,

    /** The workspace can be rendered. */
    READY,

    /** Startup failed and the user can retry. */
    FAILED,
}

/**
 * Presentation-safe startup state for the splash screen.
 *
 * @property phase Current lifecycle phase
 * @property detail Optional sanitized detail for diagnostics
 */
data class StartupStatus(
    val phase: StartupPhase,
    val detail: String? = null,
) {
    /** Returns a user-facing message without exposing server exception details. */
    fun message(): String =
        when (phase) {
            StartupPhase.STARTING_UI -> "Starting the user interface"
            StartupPhase.RESOLVING_ENDPOINT -> "Resolving server endpoint"
            StartupPhase.STARTING_SERVER -> "Starting the local server"
            StartupPhase.CONNECTING_REMOTE -> "Connecting to remote server"
            StartupPhase.LOADING_RUNTIME -> "Loading Visual Agent"
            StartupPhase.HANDSHAKING -> "Connecting to Visual Agent"
            StartupPhase.READY -> "Ready"
            StartupPhase.FAILED -> detail ?: "The server could not be started"
        }

    companion object {
        /** Creates the first state visible before the bootstrap coroutine runs. */
        fun initial(): StartupStatus = StartupStatus(StartupPhase.STARTING_UI)

        /** Creates the local server bootstrap state. */
        fun startingServer(): StartupStatus = StartupStatus(StartupPhase.STARTING_SERVER)

        /** Creates the endpoint resolution state. */
        fun resolvingEndpoint(): StartupStatus = StartupStatus(StartupPhase.RESOLVING_ENDPOINT)

        /** Creates the remote connection state. */
        fun connectingRemote(): StartupStatus = StartupStatus(StartupPhase.CONNECTING_REMOTE)

        /** Creates the runtime loading state. */
        fun loadingRuntime(): StartupStatus = StartupStatus(StartupPhase.LOADING_RUNTIME)

        /** Creates the protocol handshake state. */
        fun handshaking(): StartupStatus = StartupStatus(StartupPhase.HANDSHAKING)

        /** Creates the ready state. */
        fun ready(): StartupStatus = StartupStatus(StartupPhase.READY)

        /** Creates a safe failure state with an optional sanitized detail. */
        fun failed(detail: String? = null): StartupStatus = StartupStatus(StartupPhase.FAILED, detail)
    }
}
