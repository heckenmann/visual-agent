package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore

internal class FakeWorkspaceFileStore : WorkspaceFileStore {
    private val records = linkedMapOf<String, WorkspaceFileRecord>()

    override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
        records[record.id] = record
    }

    override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.toList()

    override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

    override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
        records.values.firstOrNull { it.relativePath == relativePath }

    override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
}
