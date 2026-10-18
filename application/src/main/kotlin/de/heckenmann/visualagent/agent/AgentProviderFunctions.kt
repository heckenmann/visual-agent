package de.heckenmann.visualagent.agent.config

import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.toReferenceName
import org.springframework.beans.factory.ObjectProvider

/** Resolves registered function names, retaining the portable name contract for standalone adapters. */
internal fun resolveProviderToolName(
    registry: ObjectProvider<ToolRegistry>?,
    id: String,
): String =
    registry
        ?.ifAvailable
        ?.toolDefinitions()
        ?.firstOrNull { it.id.value == id }
        ?.name ?: ToolId(id).toReferenceName()
