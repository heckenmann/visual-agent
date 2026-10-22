package de.heckenmann.visualagent.agent.codexdirect

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions

/** Owner-only filesystem attributes; unsupported filesystems fail closed. */
internal fun protectedAttributes(
    parent: Path,
    directory: Boolean,
): Array<FileAttribute<*>> {
    val store = Files.getFileStore(parent)
    if (store.supportsFileAttributeView("posix")) {
        return arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------")))
    }
    require(store.supportsFileAttributeView("acl")) { "Credential storage requires owner-only filesystem permissions" }
    val owner = Files.getOwner(parent, NOFOLLOW_LINKS)
    val acl =
        listOf(
            AclEntry
                .newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(AclEntryPermission.entries.toSet())
                .build(),
        )
    return arrayOf(
        object : FileAttribute<List<AclEntry>> {
            override fun name(): String = "acl:acl"

            override fun value(): List<AclEntry> = acl
        },
    )
}

/** Validates an existing credential path before opening it. */
internal fun requireProtectedPath(path: Path) {
    require(!Files.isSymbolicLink(path)) { "Credential paths must not be symbolic links" }
    val store = Files.getFileStore(path)
    if (store.supportsFileAttributeView("posix")) {
        val permissions = Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)
        require(permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") }) {
            "Credential storage permissions are not owner-only"
        }
    } else {
        val owner = Files.getOwner(path, NOFOLLOW_LINKS)
        val view = requireNotNull(Files.getFileAttributeView(path, AclFileAttributeView::class.java, NOFOLLOW_LINKS))
        require(view.acl.none { it.type() == AclEntryType.ALLOW && it.principal() != owner }) {
            "Credential storage permissions are not owner-only"
        }
    }
}
