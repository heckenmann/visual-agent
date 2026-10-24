package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Mirrors the production catalog's scrollable column so sibling controls never share click targets. */
@Composable
internal fun ProviderCatalogTestHost(content: @Composable () -> Unit) {
    MaterialTheme {
        Column(Modifier.width(360.dp).height(640.dp).verticalScroll(rememberScrollState())) {
            content()
        }
    }
}
