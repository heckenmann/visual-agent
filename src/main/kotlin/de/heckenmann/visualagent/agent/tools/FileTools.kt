package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Tool that reads UTF-8 text files inside the workspace boundary.
 */
@Component
class FileReadTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:read"),
            name = ToolId("file:read").toFunctionName(),
            description = "Read a UTF-8 text file under the workspace root. Input: {\"path\":\"relative/path\"}.",
            inputSchema = pathSchema(),
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val path =
            when (val resolved = resolveWorkspacePathOrFailure("file:read", parseObject(inputJson).requiredString("path"))) {
                is PathResolution.Failure -> return resolved.result
                is PathResolution.Success -> resolved.path
            }
        if (!path.exists() || path.isDirectory()) return failure("file:read", "File does not exist or is a directory")
        return success("file:read", path.readText(StandardCharsets.UTF_8))
    }
}

/**
 * Tool that lists immediate children of a workspace directory.
 */
@Component
class FileListTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:list"),
            name = ToolId("file:list").toFunctionName(),
            description = "List files under a workspace directory. Input: {\"path\":\"relative/path\"}.",
            inputSchema = pathSchema(),
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val path =
            when (val resolved = resolveWorkspacePathOrFailure("file:list", parseObject(inputJson).string("path") ?: ".")) {
                is PathResolution.Failure -> return resolved.result
                is PathResolution.Success -> resolved.path
            }
        if (!path.exists() || !path.isDirectory()) return failure("file:list", "Directory does not exist")
        val content =
            Files.list(path).use { stream ->
                stream
                    .sorted()
                    .map { if (it.isDirectory()) "${it.name}/" else it.name }
                    .toList()
                    .joinToString("\n")
            }
        return success("file:list", content)
    }
}

/**
 * Tool that finds workspace files matching a glob pattern.
 */
@Component
class FileGlobTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:glob"),
            name = ToolId("file:glob").toFunctionName(),
            description = "Find files by glob under the workspace root. Input: {\"pattern\":\"**/*.kt\"}.",
            inputSchema = requiredStringSchema("pattern"),
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val pattern = parseObject(inputJson).requiredString("pattern")
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val root = workspaceRoot()
        val matches =
            Files.walk(root).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { root.relativize(it).toString() }
                    .filter { matcher.matches(Path.of(it)) }
                    .limit(500)
                    .toList()
            }
        return success("file:glob", matches.joinToString("\n"))
    }
}

/**
 * Tool that searches UTF-8 files under a workspace path.
 */
@Component
class FileGrepTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:grep"),
            name = ToolId("file:grep").toFunctionName(),
            description = "Search UTF-8 files under the workspace root. Input: {\"query\":\"text\",\"path\":\"optional/path\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val query = input.requiredString("query")
        val root =
            when (val resolved = resolveWorkspacePathOrFailure("file:grep", input.string("path") ?: ".")) {
                is PathResolution.Failure -> return resolved.result
                is PathResolution.Success -> resolved.path
            }
        if (!root.exists()) return failure("file:grep", "Search path does not exist")
        val lines = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.limit(1000).forEach { file ->
                runCatching {
                    Files.readAllLines(file, StandardCharsets.UTF_8).forEachIndexed { index, line ->
                        if (line.contains(query, ignoreCase = true) && lines.size < 200) {
                            lines += "${workspaceRoot().relativize(file)}:${index + 1}: $line"
                        }
                    }
                }
            }
        }
        return success("file:grep", lines.joinToString("\n"))
    }
}

/**
 * Tool that writes UTF-8 text files inside the workspace boundary.
 */
@Component
class FileWriteTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:write"),
            name = ToolId("file:write").toFunctionName(),
            description = "Write a UTF-8 text file under the workspace root. Input: {\"path\":\"relative/path\",\"content\":\"text\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val path =
            when (val resolved = resolveWorkspacePathOrFailure("file:write", input.requiredString("path"))) {
                is PathResolution.Failure -> return resolved.result
                is PathResolution.Success -> resolved.path
            }
        path.parent?.createDirectories()
        path.writeText(input.requiredString("content"), StandardCharsets.UTF_8)
        return success("file:write", "Wrote ${workspaceRoot().relativize(path)}")
    }
}

/**
 * Tool that performs exact text replacement in a workspace file.
 */
@Component
class FileEditTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("file:edit"),
            name = ToolId("file:edit").toFunctionName(),
            description = "Replace text in a UTF-8 file. Input: {\"path\":\"relative/path\",\"oldText\":\"old\",\"newText\":\"new\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val path =
            when (val resolved = resolveWorkspacePathOrFailure("file:edit", input.requiredString("path"))) {
                is PathResolution.Failure -> return resolved.result
                is PathResolution.Success -> resolved.path
            }
        if (!path.exists() || path.isDirectory()) return failure("file:edit", "File does not exist or is a directory")
        val oldText = input.requiredString("oldText")
        val current = path.readText(StandardCharsets.UTF_8)
        if (!current.contains(oldText)) return failure("file:edit", "oldText not found")
        path.writeText(current.replace(oldText, input.requiredString("newText")), StandardCharsets.UTF_8)
        return success("file:edit", "Edited ${workspaceRoot().relativize(path)}")
    }
}
