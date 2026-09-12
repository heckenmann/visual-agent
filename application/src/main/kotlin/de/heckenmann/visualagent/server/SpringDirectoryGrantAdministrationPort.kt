package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.DirectoryGrantView
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerPage
import de.heckenmann.visualagent.workspace.DirectoryGrant
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import de.heckenmann.visualagent.workspace.ServerDirectoryBrowserService
import org.springframework.stereotype.Component

/** Spring protocol adapter for direct-user directory grant management. */
@Component
class SpringDirectoryGrantAdministrationPort(
    private val grants: DirectoryGrantService,
    private val serverDirectories: ServerDirectoryBrowserService,
) : DirectoryGrantAdministrationPort {
    override fun listGrants(): List<DirectoryGrantView> = grants.listGrants().map(::view)

    override fun listServerDirectoryRoots(pageToken: String?): ServerDirectoryPickerPage = serverDirectories.roots(pageToken)

    override fun listServerDirectoryChildren(
        selectionId: String,
        pageToken: String?,
    ): ServerDirectoryPickerPage = serverDirectories.children(selectionId, pageToken)

    override fun addServerGrant(
        selectionId: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView = view(grants.addServerGrant(serverDirectories.resolve(selectionId).toString(), displayName, mode))

    override fun addClientGrant(
        registration: ClientDirectoryCapabilityRegistration,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView = view(grants.addClientGrant(registration, displayName, mode))

    override fun reactivateClientGrant(registration: ClientDirectoryCapabilityRegistration): DirectoryGrantView =
        view(grants.reactivateClientGrant(registration))

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
            if (grant.origin == de.heckenmann.visualagent.protocol.DirectoryGrantOrigin.CLIENT) {
                "This desktop client"
            } else {
                "Application server"
            },
            grant.mode,
            grant.canonicalRoot,
            grants.isAvailable(grant),
            if (grants.isAvailable(grant)) null else "The directory is unavailable from its filesystem owner.",
        )
}
