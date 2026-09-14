package de.heckenmann.visualagent.ui.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ModalShape = RoundedCornerShape(22.dp)
private const val MODAL_ENTER_DURATION_MILLIS = 220
private const val MODAL_EXIT_DURATION_MILLIS = 150
private const val MODAL_SCRIM_ALPHA = 0.8f

/**
 * Modal host that renders all internal dialog variants in one consistent frame.
 *
 * Use cases: UC-0000071.
 *
 * @param modal Current modal request or null when no modal is visible
 * @param onDismiss Callback invoked when the modal is dismissed
 */
@Composable
fun ComposeModalHost(
    modal: ComposeModal?,
    onDismiss: () -> Unit,
) {
    var lastModal by remember { mutableStateOf<ComposeModal?>(null) }
    SideEffect {
        if (modal != null) lastModal = modal
    }
    val displayedModal = modal ?: lastModal ?: return
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = modal != null
    val scrimAlpha by animateFloatAsState(
        targetValue = if (modal != null) MODAL_SCRIM_ALPHA else 0f,
        animationSpec = tween(if (modal != null) MODAL_ENTER_DURATION_MILLIS else MODAL_EXIT_DURATION_MILLIS),
        label = "modalScrimAlpha",
    )
    val dismiss = {
        if (displayedModal is ComposeContentModal) displayedModal.onDismiss()
        onDismiss()
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(modal) {
        if (modal != null) {
            // The overlay must be attached before focus is requested. Requesting it in the
            // composition frame can both emit a FocusRequester warning and skip the enter frame.
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    if (visibility.currentState || visibility.targetState) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            dismiss()
                            true
                        } else {
                            false
                        }
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter =
                    fadeIn(animationSpec = tween(MODAL_ENTER_DURATION_MILLIS)) +
                        slideInVertically(
                            animationSpec = tween(MODAL_ENTER_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> height / 4 },
                        ),
                exit =
                    fadeOut(animationSpec = tween(MODAL_EXIT_DURATION_MILLIS)) +
                        scaleOut(
                            animationSpec = tween(MODAL_EXIT_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            targetScale = 0.96f,
                        ),
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    modalFrame(title = displayedModal.title(), onDismiss = dismiss) {
                        ComposeModalContent(modal = displayedModal, onDismiss = dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun modalFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val maximumHeight = maxHeight * 0.8f
        Card(
            modifier =
                Modifier
                    .heightIn(max = maximumHeight)
                    .widthIn(min = 420.dp, max = 760.dp)
                    .testTag("Internal modal")
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0x66 / 255f), ModalShape),
            shape = ModalShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 14.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    modalTitle(title)
                    modalSecondaryButton(label = "Close", onClick = onDismiss)
                }
                HorizontalDivider()
                CompositionLocalProvider(LocalModalBodyMaxHeight provides (maximumHeight - 64.dp)) {
                    content()
                }
            }
        }
    }
}

private fun ComposeModal.title(): String =
    when (this) {
        is ComposeConfirmationModal -> title
        is ComposeContentModal -> title
        is ComposeInfoModal -> title
        is ComposeSettingsModal -> title
        is ComposeErrorModal -> userError.summary
    }

@Composable
private fun modalTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
