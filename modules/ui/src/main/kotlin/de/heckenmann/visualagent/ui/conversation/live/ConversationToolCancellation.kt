package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import de.heckenmann.visualagent.protocol.ActivityPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Routes an explicit cancel click through the server-owned activity boundary. */
internal val LocalToolCancellation = staticCompositionLocalOf<((String, String?) -> Unit)?> { null }

/** Runs the server action off the UI thread and publishes only its short result. */
@Composable
internal fun rememberToolCancellation(
    activity: ActivityPort,
    onResult: (String) -> Unit,
): (String, String?) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(activity, scope, onResult) {
        { executionId, requestId ->
            scope.launch {
                val cancelled = withContext(Dispatchers.IO) { activity.cancelToolExecution(executionId, requestId) }
                onResult(if (cancelled) "Tool cancellation requested" else "Tool execution is no longer active")
            }
        }
    }
}

/** Reads the persisted request identity used to authorize an execution-specific cancellation. */
internal fun toolLiveRequestId(metadata: String?): String? =
    runCatching {
        Json
            .parseToJsonElement(metadata ?: return null)
            .jsonObject["requestId"]
            ?.jsonPrimitive
            ?.content
    }.getOrNull()
