package de.heckenmann.visualagent.protocol

/** Complete bidirectional contract used by the desktop presentation. */
interface ApplicationPort {
    /** Conversation commands and streaming responses. */
    val conversation: ConversationPort

    /** Todo commands and asynchronous updates. */
    val todos: TodoPort

    /** Sub-agent lifecycle and configuration commands. */
    val agents: AgentPort

    /** Provider catalog and model discovery commands. */
    val providers: ProviderPort

    /** Runtime and presentation settings. */
    val settings: SettingsPort

    /** Durable editable memory owned by the main agent. */
    val mainAgentMemory: MainAgentMemoryPort

    /** Workspace file commands. */
    val workspaceFiles: WorkspaceFilePort

    /** Explicitly approved additional directory roots. */
    val directoryAccess: DirectoryGrantAdministrationPort

    /** Structured canvas commands. */
    val canvas: CanvasPort

    /** Persisted workspace layout commands. */
    val layout: WorkspaceLayoutPort

    /** Asynchronous server-to-UI activity events. */
    val activity: ActivityPort

    /** Application lifecycle state. */
    val lifecycle: LifecyclePort

    /** Requests cancellation of all active work. */
    fun cancelActiveWork()
}
