@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown

/**
 * Renders a Markdown string using the `multiplatform-markdown-renderer` library.
 *
 * This is a thin wrapper around [com.mikepenz.markdown.m3.Markdown] to keep the
 * project's internal naming convention and allow future customization without
 * touching all call sites.
 *
 * @param markdown the raw Markdown text to render (passed 1:1, no pre-normalization)
 * @param modifier optional Compose modifier
 */
@Composable
internal fun ComposeMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = markdown,
        modifier = modifier,
    )
}
