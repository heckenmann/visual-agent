package de.heckenmann.visualagent.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.ui.modal.ComposeInfoModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Composition-owned action queue: mutations run off the UI thread in submission order. */
internal class TodoActions(
    private val scope: CoroutineScope,
    private val modalRequester: ComposeModalRequester,
) {
    private val mutex = Mutex()
    private val pending = mutableSetOf<String>()

    /** Ignores duplicate submissions of the same action until its result has been published. */
    fun submit(
        key: String,
        work: () -> Unit,
        onSuccess: () -> Unit = {},
    ) {
        if (!pending.add(key)) return
        scope.launch {
            try {
                mutex.withLock {
                    withContext(Dispatchers.IO) { work() }
                    onSuccess()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                modalRequester.request(
                    ComposeInfoModal(
                        title = "Todo action failed",
                        message =
                            error.message ?: "The operation failed. Please retry.",
                    ),
                )
            } finally {
                pending.remove(key)
            }
        }
    }
}

/** Shares one ordered action queue across all rows of a todo panel. */
@Composable
internal fun rememberTodoActions(modalRequester: ComposeModalRequester): TodoActions {
    val scope = rememberCoroutineScope()
    return remember(scope, modalRequester) { TodoActions(scope, modalRequester) }
}
