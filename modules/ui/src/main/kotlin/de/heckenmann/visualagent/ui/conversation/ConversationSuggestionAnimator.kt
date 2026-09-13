package de.heckenmann.visualagent.ui.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.BreakIterator
import java.util.Locale

/** Animates generated follow-up questions while their suggestion remains eligible. */
internal class ConversationSuggestionAnimator(
    private val stateHolder: MutableStateFlow<ConversationSuggestionUiState>,
    private val pause: suspend (Long) -> Unit,
    private val isEligible: (Long) -> Boolean,
    private val reducedMotion: () -> Boolean,
) {
    /** Renders static text for reduced motion or cycles through animated questions otherwise. */
    suspend fun animate(
        questions: List<String>,
        generation: Long,
    ) {
        if (reducedMotion()) {
            if (isEligible(generation)) {
                stateHolder.value = ConversationSuggestionUiState(text = questions.first(), phase = ConversationSuggestionPhase.HOLDING)
            }
            return
        }
        while (isEligible(generation)) {
            for (question in questions) {
                if (!isEligible(generation)) return
                type(question, generation)
                if (!isEligible(generation)) return
                blinkAndHold(question, generation)
                erase(question, generation)
                stateHolder.value = ConversationSuggestionUiState()
                pause(NEXT_QUESTION_GAP_MILLIS)
            }
        }
    }

    private suspend fun type(
        question: String,
        generation: Long,
    ) {
        val boundaries = graphemeBoundaries(question)
        stateHolder.value = ConversationSuggestionUiState(phase = ConversationSuggestionPhase.TYPING)
        for (index in 1 until boundaries.size) {
            if (!isEligible(generation)) return
            stateHolder.value =
                ConversationSuggestionUiState(
                    text = question.substring(0, boundaries[index]),
                    cursorVisible = (index / CURSOR_TOGGLE_INTERVAL_CHARS) % 2 == 0,
                    phase = ConversationSuggestionPhase.TYPING,
                )
            pause(TYPE_INTERVAL_MILLIS)
        }
    }

    private suspend fun blinkAndHold(
        question: String,
        generation: Long,
    ) {
        var elapsed = 0L
        while (elapsed < HOLD_INTERVAL_MILLIS) {
            if (!isEligible(generation)) return
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

    private suspend fun erase(
        question: String,
        generation: Long,
    ) {
        val boundaries = graphemeBoundaries(question)
        for (index in boundaries.lastIndex - 1 downTo 0) {
            if (!isEligible(generation)) return
            stateHolder.value =
                ConversationSuggestionUiState(
                    text = question.substring(0, boundaries[index]),
                    cursorVisible = (index / CURSOR_TOGGLE_INTERVAL_CHARS) % 2 == 0,
                    phase = ConversationSuggestionPhase.ERASING,
                )
            pause(ERASE_INTERVAL_MILLIS)
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

    private companion object {
        const val TYPE_INTERVAL_MILLIS = 45L
        const val CURSOR_BLINK_MILLIS = 500L
        const val HOLD_INTERVAL_MILLIS = 2_000L
        const val ERASE_INTERVAL_MILLIS = 25L
        const val NEXT_QUESTION_GAP_MILLIS = 250L
        const val CURSOR_TOGGLE_INTERVAL_CHARS = 12
    }
}
