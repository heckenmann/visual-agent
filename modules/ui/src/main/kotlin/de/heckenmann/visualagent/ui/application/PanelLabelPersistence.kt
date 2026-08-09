package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.config.AppConfigBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PanelLabelPersistence(
    private val config: AppConfigBean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingWrite: Job? = null
    private var closed = false

    @Synchronized
    fun persist() {
        if (!closed) {
            pendingWrite =
                scope.launch {
                    config.savePanelLabels()
                }
        }
    }

    fun flushAndClose() {
        val write =
            synchronized(this) {
                closed = true
                pendingWrite
            }
        runBlocking { write?.join() }
        scope.cancel()
    }
}

internal fun closeVisualAgentApplication(
    dependencies: ComposeApplicationDependencies,
    saveStageOnExit: () -> Unit,
    panelLabelPersistence: PanelLabelPersistence,
    exitApplication: () -> Unit,
) {
    dependencies.lifecycle.beginShutdown()
    saveStageOnExit()
    panelLabelPersistence.flushAndClose()
    dependencies.close()
    exitApplication()
}
