package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.stereotype.Component

/**
 * Exposes built-in manual pages for tools and markdown formatting.
 *
 * Supported actions:
 * - `list`: list all documented entries.
 * - `show`: render one manual page by topic.
 */
@Component
class ManualTool(
    private val allTools: List<VisualAgentTool> = emptyList(),
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("manual"),
            name = ToolId("manual").toFunctionName(),
            description =
                "Show built-in manual pages. Use action=list for all topics or action=show with a topic. " +
                    "Includes markdown format guidance via topic=markdown.",
            inputSchema = STRING_SCHEMA,
        )

    /**
     * Renders a built-in manual page or lists available manual topics.
     *
     * @param inputJson JSON payload with `action` and optional `topic`
     * @param context Request metadata, not used by this tool
     * @return Manual output or an error with available topics
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
