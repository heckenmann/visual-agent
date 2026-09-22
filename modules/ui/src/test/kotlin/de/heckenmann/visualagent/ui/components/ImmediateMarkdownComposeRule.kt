package de.heckenmann.visualagent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.coroutines.EmptyCoroutineContext

/** Compose test rule that makes the external Markdown parser complete during composition. */
internal class ImmediateMarkdownComposeRule(
    private val delegate: ComposeContentTestRule = createComposeRule(),
) : ComposeContentTestRule by delegate {
    override fun setContent(composable: @Composable () -> Unit) {
        delegate.setContent {
            CompositionLocalProvider(
                LocalMarkdownImmediateParsing provides true,
                LocalMarkdownImageIoContext provides EmptyCoroutineContext,
                LocalMarkdownImageDecodeContext provides EmptyCoroutineContext,
            ) {
                composable()
            }
        }
    }
}
