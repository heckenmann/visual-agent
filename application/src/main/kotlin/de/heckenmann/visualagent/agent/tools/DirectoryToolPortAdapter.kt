package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryEntry
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMatch
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import org.springframework.stereotype.Component

/** Server-owned adapter between the model tool and directory authorization service. */
@Component
class DirectoryToolPortAdapter(
    private val grants: DirectoryGrantService,
) : DirectoryToolPort {
    override fun listGrants(): List<ToolDirectoryGrant> =
        grants.listGrants().map { ToolDirectoryGrant(it.id, it.displayName, it.origin.name, it.mode.name, grants.isAvailable(it)) }

    override fun list(
        grantId: String,
        path: String,
    ): List<ToolDirectoryEntry> = grants.list(grantId, path).map { ToolDirectoryEntry(it.path, it.directory, it.sizeBytes) }

    override fun readText(
        grantId: String,
        path: String,
    ): String = grants.readText(grantId, path)

    override fun search(
        grantId: String,
        query: String,
    ): List<ToolDirectoryMatch> = grants.search(grantId, query).map { ToolDirectoryMatch(it.path, it.line, it.snippet) }

    override fun writeText(
        grantId: String,
        path: String,
        content: String,
    ): String = grants.writeText(grantId, path, content)
}
