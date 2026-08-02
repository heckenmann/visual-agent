package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class JpaWorkspaceFileStore(
    private val repository: WorkspaceFileRepository,
) : WorkspaceFileStore {
    @Transactional
    override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
        repository.save(record.toEntity())
    }

    @Transactional(readOnly = true)
    override fun listWorkspaceFiles(): List<WorkspaceFileRecord> =
        repository.findAllByOrderByImportedAtDescIdDesc().map(WorkspaceFileEntity::toRecord)

    @Transactional(readOnly = true)
    override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = repository.findById(id).orElse(null)?.toRecord()

    @Transactional(readOnly = true)
    override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
        repository.findByRelativePath(relativePath)?.toRecord()

    @Transactional
    override fun deleteWorkspaceFile(id: String): Boolean {
        if (!repository.existsById(id)) return false
        repository.deleteById(id)
        return true
    }
}

private fun WorkspaceFileRecord.toEntity(): WorkspaceFileEntity =
    WorkspaceFileEntity(
        id = id,
        relativePath = relativePath,
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        extractedText = extractedText,
        importedAt = importedAt,
        updatedAt = updatedAt,
    )

private fun WorkspaceFileEntity.toRecord(): WorkspaceFileRecord =
    WorkspaceFileRecord(
        id = id,
        relativePath = relativePath,
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        extractedText = extractedText,
        importedAt = importedAt,
        updatedAt = updatedAt,
    )
