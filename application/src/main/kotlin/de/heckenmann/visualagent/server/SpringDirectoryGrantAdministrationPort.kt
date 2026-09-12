package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryAccessPort
import de.heckenmann.visualagent.protocol.DirectoryGrantView
import de.heckenmann.visualagent.workspace.DirectoryGrant
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import org.springframework.stereotype.Component

/** Spring protocol adapter for direct-user directory grant management. */
@Component
class SpringDirectoryAccessPort(
    private val grants: DirectoryGrantService,
) : DirectoryAccessPort {
    override fun listGrants(): List<DirectoryGrantView> = grants.listGrants().map(::view)

    override fun inspectServerDirectory(absolutePath: String): DirectoryGrantView {
        val canonical = grants.inspectServerDirectory(absolutePath)
        return DirectoryGrantView(
            "",
            canonical.fileName?.toString() ?: canonical.toString(),
            de.heckenmann.visualagent.protocol.DirectoryGrantOrigin.SERVER,
            DirectoryAccessMode.READ_ONLY,
            canonical.toString(),
            true,
            null,
        )
    }

    override fun addServerGrant(
        absolutePath: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView = view(grants.addServerGrant(absolutePath, displayName, mode))

    override fun updateGrant(
        id: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView = view(grants.updateGrant(id, displayName, mode))

    override fun removeGrant(id: String): Boolean = grants.removeGrant(id)

    private fun view(grant: DirectoryGrant): DirectoryGrantView =
        DirectoryGrantView(
            grant.id,
            grant.displayName,
            grant.origin,
            grant.mode,
            grant.canonicalRoot,
            grants.isAvailable(grant),
            if (grants.isAvailable(grant)) null else "The directory is unavailable from its filesystem owner.",
        )
}
