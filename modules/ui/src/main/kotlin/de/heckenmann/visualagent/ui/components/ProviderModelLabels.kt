package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.protocol.ProviderModel

/** Keep legacy plain model names when a provider has not reported capabilities. */
internal fun ProviderModel.labelWithCapabilities(): String =
    if (capabilities.isEmpty()) {
        name
    } else {
        "$name [${capabilities.sorted().joinToString(", ")}]"
    }
