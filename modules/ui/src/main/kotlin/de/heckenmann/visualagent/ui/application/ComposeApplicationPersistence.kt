package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.config.AppConfigBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun CoroutineScope.persistPanelLabels(config: AppConfigBean) {
    launch {
        withContext(Dispatchers.IO) {
            config.savePanelLabels()
        }
    }
}
