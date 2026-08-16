package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import org.springframework.context.annotation.Lazy

/**
 * Exposes built-in manual pages for tools and markdown formatting.
 *
 * Supported actions:
 * - `list`: list all documented entries.
 * - `show`: render one manual page by topic.
 *
 * Use cases: UC-0000058.
 */
@AgentTool
class ManualTool(
    @param:Lazy private val allTools: List<VisualAgentTool> = emptyList(),
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("manual"),
            name = ToolId("manual").toFunctionName(),
            description =
                "Show built-in manual pages for tools and markdown formatting. Actions:\n" +
                    "- list: {\"action\":\"list\"}. Lists all available topics.\n" +
                    "- show: {\"action\":\"show\",\"topic\":\"todos\"}. Shows a manual page for a tool or topic. " +
                    "Use topic=markdown for markdown formatting reference. " +
                    "Use topic=<tool_id> (e.g. file:read, todos, canvas) for tool-specific documentation.",
            inputSchema = STRING_SCHEMA,
        )

    /**
     * Renders a built-in manual page or lists available manual topics.
     *
     * @param inputJson JSON payload with `action` and optional `topic`
     * @param context Request metadata, not used by this tool
     * @return Manual output or an error with available topics
     * @see docs/usecases/uc_0000058_get_builtin_manual.md
     */
    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return when ((input.string("action") ?: "show").lowercase()) {
            "list" -> success("manual", availableTopics().joinToString("\n") { "- $it" })
            "show" -> showTopic(input.string("topic") ?: "index")
            else -> failure("manual", "Unsupported manual action. Use 'list' or 'show'.")
        }
    }

    private fun showTopic(topicInput: String): ToolResult {
        val normalized = normalizeTopic(topicInput)
        val pages = manualPages()
        val content =
            pages[normalized]
                ?: return failure(
                    "manual",
                    "Unknown topic '$topicInput'. Available topics: ${availableTopics().joinToString(", ")}",
                )
        return success("manual", content)
    }

    private fun normalizeTopic(topic: String): String =
        topic
            .trim()
            .lowercase()
            .replace(":", "_")
            .replace("-", "_")
            .replace(" ", "_")

    private fun availableTopics(): List<String> = manualPages().keys.sorted()

    private fun manualPages(): Map<String, String> =
        buildMap {
            val toolDefinitions = allTools.map { it.definition }.sortedBy { it.name }
            put(
                "index",
                """
                # Visual Agent Manual
                
                ## Usage
                - `{"action":"list"}`
                - `{"action":"show","topic":"markdown"}`
                - `{"action":"show","topic":"todos"}`
                
                ## Available Topics
                ${toolDefinitions.joinToString("\n") { "- ${it.name}" }}
                - markdown
                """.trimIndent(),
            )
            put("markdown", markdownReference())

            toolDefinitions.forEach { tool ->
                val manual = toolReference(tool)
                val aliases =
                    setOf(
                        tool.id.value,
                        tool.id.value.replace(":", "_"),
                        tool.name,
                        tool.name.replace(":", "_"),
                    ).map(::normalizeTopic)
                aliases.forEach { alias ->
                    put(alias, manual)
                }
            }
        }

    private fun toolReference(tool: ToolDefinition): String =
        """
        # ${tool.name}
        
        - **Tool ID:** `${tool.id.value}`
        - **Function name:** `${tool.name}`
        
        ## Description
        ${tool.description}
        
        ## Input Schema (JSON)
        ```json
        ${tool.inputSchema}
        ```
        """.trimIndent()

    private fun markdownReference(): String =
        """
        # Markdown Quick Reference (CommonMark-compatible)
        
        ## Headers
        # H1
        ## H2
        ### H3
        
        ## Emphasis
        *italic*  **bold**  ~~strikethrough~~
        
        ## Lists
        - item
        - item
        1. first
        2. second
        
        ## Links and Images
        [label](https://example.com)
        ![alt](https://example.com/image.png)

        ### Conversation Image Sources
        Use a complete image node with meaningful alt text:
        ![diagram](workspace:generated/diagram.png)
        ![diagram](server-file:generated/diagram.png)
        ![diagram](https://example.com/diagram.png)
        ![diagram](data:image/png;base64,<validated-data>)
        `workspace:` and `server-file:` refer to server-managed files. `client-file:` is reserved for an exact client-local path supplied by the user. Use only sources returned by a tool or supplied by the user; do not invent paths or base64 data. Remote URLs must point directly to image bytes. A canvas capture is stored as a conversation attachment automatically.

        ## Code
        `inline code`
        ```kotlin
        val x = 1
        ```
        
        ## Quote and Rule
        > quoted line
        ---
        
        ## Table
        | Col A | Col B |
        |------:|:------|
        | A     | B     |
        """.trimIndent()
}
