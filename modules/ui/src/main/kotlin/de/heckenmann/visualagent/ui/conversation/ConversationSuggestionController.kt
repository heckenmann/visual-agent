package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionRequest
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.util.Locale

/** Rendering phase for an idle follow-up suggestion. */
internal enum class ConversationSuggestionPhase {
    IDLE,
    WAITING_FOR_IDLE,
    REQUESTING,
    TYPING,
    HOLDING,
    ERASING,
}

/** Immutable presentation state for the ghost suggestion inside the composer. */
internal data class ConversationSuggestionUiState(
    val text: String = "",
    val cursorVisible: Boolean = false,
    val phase: ConversationSuggestionPhase = ConversationSuggestionPhase.IDLE,
)

/**
 * Coordinates idle-turn eligibility, one provider request, and deterministic ghost-text animation.
 *
 * The controller owns no Compose state and can therefore be exercised with a test coroutine scope.
 */
internal class ConversationSuggestionController(
    private val port: ConversationSuggestionPort,
    private val scope: CoroutineScope,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val stateHolder = MutableStateFlow(ConversationSuggestionUiState())
    private val lock = Any()
    private var animationJob: Job? = null
    private var requestToken: CancellationTokenImpl? = null
    private var generation = 0L
    private var pendingCompletion: ConversationCompletionEvent? = null
    private var settings = SuggestionSettings()
    private var input = ""
    private var sending = false
    private var queuedMessages = 0
    private var previousQuestions: List<String> = emptyList()
    private var closed = false

    /** Current ghost-text rendering state. */
    val state: StateFlow<ConversationSuggestionUiState> = stateHolder.asStateFlow()

    /** Applies persisted settings without restarting the conversation panel. */
    fun updateSettings(snapshot: SettingsSnapshot) {
        synchronized(lock) {
            settings =
                SuggestionSettings(
                    enabled = snapshot.followUpSuggestionsEnabled,
                    delaySeconds = snapshot.followUpSuggestionIdleDelaySeconds,
                    questionCount = snapshot.followUpSuggestionCount,
                )
            if (!settings.enabled) {
                cancelLocked(clearPending = true)
            } else {
                scheduleLocked()
            }
        }
    }

    /** Records a successfully persisted assistant response as a possible suggestion anchor. */
    fun onCompletion(event: ConversationCompletionEvent) {
        synchronized(lock) {
            if (closed) return
            generation++
            pendingCompletion = event
            cancelLocked(clearPending = false)
            scheduleLocked()
        }
    }

    /** Cancels the current turn when the user interacts with the composer. */
    fun onUserInteraction() {
        synchronized(lock) {
            if (closed) return
            generation++
            cancelLocked(clearPending = true)
        }
    }

    /** Updates draft state and treats every user edit as interaction, including deletion to empty. */
    fun onInputChanged(value: String) {
        synchronized(lock) {
            input = value
            onUserInteractionLocked()
        }
    }

    /**
     * Records focus changes without treating focus alone as a draft edit.
     *
     * A focused but empty composer may still show the ghost suggestion; the first actual input
     * change cancels it through [onInputChanged].
     */
    fun onFocusChanged(_isFocused: Boolean) {
        synchronized(lock) {
            if (closed) return
            // Focus alone is not an edit. Keep an eligible ghost suggestion visible until the
            // user actually changes the draft; this also supports the permanently focused
            // composer used by the desktop shell.
            scheduleLocked()
        }
    }

    /** Updates whether a model request is currently active. */
    fun onSendingChanged(isSending: Boolean) {
        synchronized(lock) {
            sending = isSending
            if (isSending) {
                onUserInteractionLocked()
            } else {
                scheduleLocked()
            }
        }
    }

    /** Updates queue state; queued work prevents suggestions until the queue is empty. */
    fun onQueueSizeChanged(size: Int) {
        synchronized(lock) {
            queuedMessages = size.coerceAtLeast(0)
            if (queuedMessages > 0) {
                onUserInteractionLocked()
            } else {
                scheduleLocked()
            }
        }
    }

    /** Cancels timers and provider work when the panel leaves composition. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation++
            cancelLocked(clearPending = true)
        }
    }

    private fun onUserInteractionLocked() {
        generation++
        cancelLocked(clearPending = true)
    }

    private fun cancelLocked(clearPending: Boolean) {
        animationJob?.cancel()
        animationJob = null
        requestToken?.cancel()
        requestToken = null
        if (clearPending) pendingCompletion = null
        stateHolder.value = ConversationSuggestionUiState()
    }

    private fun scheduleLocked() {
        if (closed || !settings.enabled || !eligibleLocked() || pendingCompletion == null || animationJob?.isActive == true) return
        val event = pendingCompletion ?: return
        val runGeneration = generation
        val runSettings = settings
        animationJob =
            scope
                .launch {
                    stateHolder.value = ConversationSuggestionUiState(phase = ConversationSuggestionPhase.WAITING_FOR_IDLE)
                    pause(runSettings.delaySeconds * 1_000L)
                    if (!isEligible(runGeneration)) return@launch
                    val token = CancellationTokenImpl()
                    synchronized(lock) {
                        if (!isEligibleLocked(runGeneration)) return@launch
                        requestToken = token
                        stateHolder.value = ConversationSuggestionUiState(phase = ConversationSuggestionPhase.REQUESTING)
                    }
                    val result =
                        runCatching {
                            port.generate(
                                ConversationSuggestionRequest(event.assistantEntryId, previousQuestions.takeLast(5)),
                                token,
                            )
                        }.getOrNull()
                    synchronized(lock) {
                        requestToken = null
                        if (!isEligibleLocked(runGeneration)) return@launch
                    }
                    if (result?.assistantEntryId != event.assistantEntryId) {
                        stateHolder.value = ConversationSuggestionUiState()
                        return@launch
                    }
                    val questions = result.questions
                    if (questions.size != runSettings.questionCount || questions.any { it.isBlank() || it.length > MAX_QUESTION_LENGTH }) {
                        stateHolder.value = ConversationSuggestionUiState()
                        return@launch
                    }
                    previousQuestions = questions
                    animateQuestions(questions, runGeneration)
                }.also { job ->
                    job.invokeOnCompletion {
                        synchronized(lock) {
                            if (animationJob === job) animationJob = null
                        }
                    }
                }
    }

    private fun isEligible(runGeneration: Long): Boolean = synchronized(lock) { isEligibleLocked(runGeneration) }

    private fun isEligibleLocked(runGeneration: Long): Boolean =
        !closed && generation == runGeneration && settings.enabled && pendingCompletion != null && eligibleLocked()

    private fun eligibleLocked(): Boolean = input.isBlank() && !sending && queuedMessages == 0

    private suspend fun animateQuestions(
        questions: List<String>,
        runGeneration: Long,
    ) {
        while (isEligible(runGeneration)) {
            for (question in questions) {
                if (!isEligible(runGeneration)) return
                val boundaries = graphemeBoundaries(question)
                stateHolder.value = ConversationSuggestionUiState(phase = ConversationSuggestionPhase.TYPING)
                for (index in 1 until boundaries.size) {
                    if (!isEligible(runGeneration)) return
                    val visible = question.substring(0, boundaries[index])
                    stateHolder.value =
                        ConversationSuggestionUiState(
                            text = visible,
                            cursorVisible = (index / CURSOR_TOGGLE_INTERVAL_CHARS) % 2 == 0,
                            phase = ConversationSuggestionPhase.TYPING,
                        )
                    pause(TYPE_INTERVAL_MILLIS)
                }
                if (!isEligible(runGeneration)) return
                blinkAndHold(question, runGeneration)
                val eraseBoundaries = graphemeBoundaries(question)
                for (index in eraseBoundaries.lastIndex - 1 downTo 0) {
                    if (!isEligible(runGeneration)) return
                    stateHolder.value =
                        ConversationSuggestionUiState(
                            text = question.substring(0, eraseBoundaries[index]),
                            cursorVisible = (index / CURSOR_TOGGLE_INTERVAL_CHARS) % 2 == 0,
                            phase = ConversationSuggestionPhase.ERASING,
                        )
                    pause(ERASE_INTERVAL_MILLIS)
                }
                stateHolder.value = ConversationSuggestionUiState()
                pause(NEXT_QUESTION_GAP_MILLIS)
            }
        }
    }

    private suspend fun blinkAndHold(
        question: String,
        runGeneration: Long,
    ) {
        var elapsed = 0L
        while (elapsed < HOLD_INTERVAL_MILLIS) {
            if (!isEligible(runGeneration)) return
            stateHolder.value =
                ConversationSuggestionUiState(
                    text = question,
                    cursorVisible = (elapsed / CURSOR_BLINK_MILLIS) % 2L == 0L,
                    phase = ConversationSuggestionPhase.HOLDING,
                )
            val step = CURSOR_BLINK_MILLIS.coerceAtMost(HOLD_INTERVAL_MILLIS - elapsed)
            pause(step)
            elapsed += step
        }
    }

    private fun graphemeBoundaries(value: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(value)
        return buildList {
            add(iterator.first())
            var boundary = iterator.next()
            while (boundary != BreakIterator.DONE) {
                add(boundary)
                boundary = iterator.next()
            }
        }
    }

    private data class SuggestionSettings(
        val enabled: Boolean = true,
        val delaySeconds: Int = 3,
        val questionCount: Int = 3,
    )

    private companion object {
        private const val MAX_QUESTION_LENGTH = 140
        private const val TYPE_INTERVAL_MILLIS = 45L
        private const val CURSOR_BLINK_MILLIS = 500L
        private const val HOLD_INTERVAL_MILLIS = 2_000L
        private const val ERASE_INTERVAL_MILLIS = 25L
        private const val NEXT_QUESTION_GAP_MILLIS = 250L
        private const val CURSOR_TOGGLE_INTERVAL_CHARS = 12
    }
}

/** Remembers and wires a suggestion controller to the server port and persisted settings. */
@Composable
internal fun rememberConversationSuggestionController(
    suggestionPort: ConversationSuggestionPort,
    settingsPort: SettingsPort,
): ConversationSuggestionController {
    val scope = rememberCoroutineScope()
    val controller = remember(suggestionPort) { ConversationSuggestionController(suggestionPort, scope) }
    DisposableEffect(suggestionPort) {
        val handle = suggestionPort.addCompletionListener(controller::onCompletion)
        onDispose {
            handle.close()
            controller.close()
        }
    }
    DisposableEffect(settingsPort) {
        val handle = settingsPort.addChangeListener(controller::updateSettings)
        onDispose { handle.close() }
    }
    LaunchedEffect(settingsPort) {
        controller.updateSettings(settingsPort.snapshotAsync())
    }
    return controller
}
