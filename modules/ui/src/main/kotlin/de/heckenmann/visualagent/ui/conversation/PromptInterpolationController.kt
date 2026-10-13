package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.validateInterpolationDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns transient composer state, with provider work delegated to the protocol adapter. */
internal class PromptInterpolationController(
    private val conversation: ConversationPort,
    private val settings: SettingsPort,
    private val scope: CoroutineScope,
    private val input: () -> String,
    private val replaceInput: (String) -> Unit,
    private val send: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val mutableBusy = MutableStateFlow(false)

    /** Prevents duplicate actions during settings loading or model work. */
    val busy = mutableBusy.asStateFlow()
    private val mutableInterpolating = MutableStateFlow(false)

    /** Controls the Magic animation while the interpolation request runs. */
    val interpolating = mutableInterpolating.asStateFlow()
    private val mutableNotice = MutableStateFlow("")

    /** Review guidance or safe failure feedback displayed beside the composer. */
    val notice = mutableNotice.asStateFlow()
    private var revision = 0L
    private var preparedRevision: Long? = null
    private var preparedText: String? = null
    private var job: Job? = null
    private var token: CancellationTokenImpl? = null

    @Volatile
    private var automaticInterpolationSetting: Boolean? = null

    /** Keeps the send path in sync with settings without rereading them for every message. */
    fun onSettingsChanged(enabled: Boolean) {
        automaticInterpolationSetting = enabled
    }

    /** Invalidates pending and prepared results, including edits back to the previous text. */
    fun edited() {
        mutableNotice.value = ""
        cancel(report = false)
        preparedRevision = null
        preparedText = null
    }

    /** Rewrites without sending, leaving the result available for review. */
    fun manual() = start(automatic = false)

    /** Applies the persisted automatic setting once before the send/queue action. */
    fun submit() = start(automatic = true)

    /** Stops auxiliary work while preserving the draft. */
    fun cancel(report: Boolean = true) {
        revision++
        val wasBusy = mutableBusy.value
        token?.cancel()
        job?.cancel()
        token = null
        job = null
        mutableBusy.value = false
        mutableInterpolating.value = false
        if (report && wasBusy) status("Prompt interpolation cancelled; edit or send the original prompt.")
    }

    private fun start(automatic: Boolean) {
        if (mutableBusy.value) return
        val original = input()
        if (original.isBlank()) return
        val requestRevision = revision
        mutableNotice.value = ""
        mutableBusy.value = true
        job =
            scope.launch {
                try {
                    val rewrite =
                        if (!automatic) {
                            true
                        } else {
                            try {
                                automaticInterpolationSetting
                                    ?: settings.snapshotAsync().automaticPromptInterpolation.also { fetched ->
                                        if (automaticInterpolationSetting == null) automaticInterpolationSetting = fetched
                                    }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Failure of an optional setting must never suppress a normal chat send.
                                if (current(requestRevision, original)) {
                                    status("Prompt interpolation settings unavailable; sending the original prompt.")
                                }
                                false
                            }
                        }
                    if (!current(requestRevision, original)) return@launch
                    val prepared = preparedRevision == requestRevision && preparedText == original
                    val result = if (rewrite && (!automatic || !prepared)) rewrite(original) else original
                    if (!current(requestRevision, original)) return@launch
                    if (rewrite) {
                        replaceInput(result)
                        preparedRevision = requestRevision
                        preparedText = result
                    }
                    if (automatic) send(result) else status("Prompt refined; review it before sending.")
                } catch (cancelled: CancellationException) {
                    if (current(requestRevision, original)) status("Prompt interpolation cancelled; retry or send the original prompt.")
                    throw cancelled
                } catch (_: Exception) {
                    if (current(requestRevision, original)) {
                        status(
                            "Prompt interpolation failed. Check the selected provider/model and retry, " +
                                "or send the original with automatic interpolation disabled.",
                        )
                    }
                } finally {
                    if (revision == requestRevision) {
                        token?.cancel()
                        token = null
                        mutableBusy.value = false
                        mutableInterpolating.value = false
                    }
                }
            }
    }

    private suspend fun rewrite(original: String): String {
        validateInterpolationDraft(original)
        val cancellation = CancellationTokenImpl()
        token = cancellation
        mutableInterpolating.value = true
        val result = conversation.interpolate(original, cancellation).trim()
        validateInterpolationDraft(result)
        return result
    }

    private fun status(message: String) {
        mutableNotice.value = message
        onStatus(message)
    }

    private fun current(
        requestRevision: Long,
        original: String,
    ): Boolean = revision == requestRevision && input() == original
}
