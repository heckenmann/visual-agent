package de.heckenmann.visualagent.ui

import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.util.concurrent.atomic.AtomicBoolean

/** An event-driven Compose idling resource for work owned by a test double. */
internal class CompletionIdlingResource(
    private val description: String,
) : IdlingResource {
    private val completed = AtomicBoolean(false)

    override val isIdleNow: Boolean
        get() = completed.get()

    override fun getDiagnosticMessageIfBusy(): String = "Waiting for $description."

    fun complete() {
        completed.set(true)
    }
}

/** Executes [action] while Compose tracks [resource] as in-flight work. */
internal fun ComposeContentTestRule.awaitCompletion(
    resource: CompletionIdlingResource,
    action: () -> Unit,
) {
    registerIdlingResource(resource)
    try {
        runWithoutImplicitWait(action)
        waitForIdle()
    } finally {
        unregisterIdlingResource(resource)
    }
}
