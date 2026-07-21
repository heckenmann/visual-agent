package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Exposes packaged Visual Agent use-case documents to enabled model calls.
 *
 * Use cases: UC-0000067.
 */
@Component
class UseCaseTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description = useCaseToolDescription(),
            inputSchema = STRING_SCHEMA,
        )

    /**
     * Executes a use-case catalog lookup.
     *
     * @param inputJson JSON payload with `action` and optional `id`, `file`, `query`, or `limit`
     * @param context Request metadata, not used by this tool
     * @return Markdown catalog output or a structured error
     * @see docs/usecases/uc_0000067_query_use_case_catalog.md
     */
    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        runCatching {
            val input = parseObject(inputJson)
            when ((input.string("action") ?: "list").lowercase()) {
                "list" -> listUseCases(input.int("limit") ?: DEFAULT_LIST_LIMIT)
                "show" -> showUseCase(input.string("id"), input.string("file"))
                "search" -> searchUseCases(input.requiredString("query"), input.int("limit") ?: DEFAULT_SEARCH_LIMIT)
                else -> failure(TOOL_ID, "Unsupported usecases action. Use 'list', 'show', or 'search'.")
            }
        }.getOrElse { failure(TOOL_ID, it.message ?: "Use case lookup failed") }

    private fun listUseCases(limit: Int): ToolResult {
        val documents = loadDocuments().take(limit.coerceIn(1, MAX_RESULTS))
        val content =
            buildString {
                appendLine("# Visual Agent Use Cases")
                documents.forEach { document ->
                    appendLine("- ${document.id}: ${document.title} (`${document.fileName}`)")
                }
            }.trimEnd()
        return success(TOOL_ID, content)
    }

    private fun showUseCase(
        id: String?,
        file: String?,
    ): ToolResult {
        val document =
            loadDocuments()
                .firstOrNull { it.matches(id, file) }
                ?: return failure(TOOL_ID, "Unknown use case. Provide a valid id like UC-0000067 or packaged file name.")
        return success(TOOL_ID, document.content)
    }

    private fun searchUseCases(
        query: String,
        limit: Int,
    ): ToolResult {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Search query must not be blank" }
        val matches =
            loadDocuments()
                .filter { it.matchesQuery(normalizedQuery) }
                .take(limit.coerceIn(1, MAX_RESULTS))
        val content =
            if (matches.isEmpty()) {
                "No use cases matched '$normalizedQuery'."
            } else {
                buildString {
                    appendLine("# Use Case Search Results")
                    matches.forEach { document ->
                        appendLine("- ${document.id}: ${document.title} (`${document.fileName}`)")
                        appendLine("  ${document.snippet(normalizedQuery)}")
                    }
                }.trimEnd()
            }
        return success(TOOL_ID, content)
    }

    private fun loadDocuments(): List<UseCaseDocument> =
        loadPackagedIndex()
            .ifEmpty { loadFilesystemIndex() }
            .mapNotNull(::loadDocument)
            .sortedBy { it.fileName }

    private fun loadPackagedIndex(): List<String> =
        readResourceText("$RESOURCE_ROOT/index.txt")
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(::isValidUseCaseFileName)
            ?.toList()
            .orEmpty()

    private fun loadFilesystemIndex(): List<String> {
        val root = workspaceRoot().resolve("docs/usecases")
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            stream
                .filter { it.isRegularFile() && isValidUseCaseFileName(it.name) }
                .map { it.name }
                .sorted()
                .toList()
        }
    }

    private fun loadDocument(fileName: String): UseCaseDocument? {
        if (!isValidUseCaseFileName(fileName)) return null
        val content =
            readResourceText("$RESOURCE_ROOT/$fileName")
                ?: readFilesystemText(fileName)
                ?: return null
        return UseCaseDocument(
            fileName = fileName,
            id = fileName.toUseCaseId(),
            title = content.titleOrFallback(fileName),
            content = content,
        )
    }

    private fun readFilesystemText(fileName: String): String? {
        val root = workspaceRoot().resolve("docs/usecases").normalize()
        val file = root.resolve(fileName).normalize()
        if (!file.startsWith(root) || !file.isRegularFile()) return null
        return file.readText()
    }

    private fun readResourceText(name: String): String? =
        javaClass.classLoader
            .getResourceAsStream(name)
            ?.bufferedReader()
            ?.use { it.readText() }

    private fun UseCaseDocument.matches(
        requestedId: String?,
        requestedFile: String?,
    ): Boolean =
        when {
            requestedFile != null -> isValidUseCaseFileName(requestedFile) && fileName == requestedFile
            requestedId != null -> id.equals(normalizeUseCaseId(requestedId), ignoreCase = true)
            else -> false
        }

    private fun UseCaseDocument.matchesQuery(query: String): Boolean =
        content.contains(query, ignoreCase = true) ||
            title.contains(query, ignoreCase = true) ||
            id.equals(normalizeUseCaseId(query), ignoreCase = true)

    private fun String.titleOrFallback(fileName: String): String =
        lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?: fileName.removeSuffix(".md").replace('_', ' ')

    private fun String.toUseCaseId(): String = "UC-${substringAfter("uc_").take(7)}"

    private fun normalizeUseCaseId(id: String): String =
        id
            .trim()
            .removePrefix("uc_")
            .removePrefix("UC_")
            .removePrefix("uc-")
            .removePrefix("UC-")
            .padStart(7, '0')
            .let { "UC-$it" }

    private fun isValidUseCaseFileName(fileName: String): Boolean = USE_CASE_FILE_REGEX.matches(fileName)

    private fun UseCaseDocument.snippet(query: String): String {
        val line =
            content
                .lineSequence()
                .firstOrNull { it.contains(query, ignoreCase = true) && !it.startsWith("# ") }
                ?.trim()
                .orEmpty()
        return line.ifBlank { "Matched by title or ID." }.take(MAX_SNIPPET_LENGTH)
    }

    private data class UseCaseDocument(
        val fileName: String,
        val id: String,
        val title: String,
        val content: String,
    )

    private companion object {
        const val TOOL_ID = "usecases"
        const val RESOURCE_ROOT = "usecases"
        const val DEFAULT_LIST_LIMIT = 100
        const val DEFAULT_SEARCH_LIMIT = 20
        const val MAX_RESULTS = 250
        const val MAX_SNIPPET_LENGTH = 180
        val USE_CASE_FILE_REGEX = Regex("""uc_\d{7}_[a-z0-9_]+\.md""")

        fun useCaseToolDescription(): String =
            "List, show, and search packaged Visual Agent use-case documents. Actions:\n" +
                "- list: {\"action\":\"list\",\"limit\":100}. Lists all use cases with ID and title.\n" +
                "- show: {\"action\":\"show\",\"id\":\"UC-0000067\"} or " +
                "{\"action\":\"show\",\"file\":\"uc_0000067_query_use_case_catalog.md\"}. Shows full use case content.\n" +
                "- search: {\"action\":\"search\",\"query\":\"canvas\",\"limit\":20}. Searches use case content by keyword."
    }
}
