package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Assigns a todo to a specific sub-agent.
 */
@Component
class AgentAssignTodoTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-todo"),
            name = ToolId("agent:assign-todo").toFunctionName(),
            description = "Assign a todo to a specific sub-agent. Input: {\"todoId\":\"todo-1\",\"agentId\":\"agent-1\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val todoId = input.requiredString("todoId")
        val agentId = input.requiredString("agentId")
        return if (agentManager.assignTodoToAgent(todoId, agentId)) {
            success("agent:assign-todo", "Assigned todo $todoId to agent $agentId")
        } else {
            failure("agent:assign-todo", "Assignment failed")
        }
    }
}

/**
 * Assigns the next pending todo to an idle sub-agent.
 */
@Component
class AgentAssignNextTodoTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-next-todo"),
            name = ToolId("agent:assign-next-todo").toFunctionName(),
            description = "Assign the next pending todo to an idle sub-agent.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        if (agentManager.assignNextTodo()) {
            success("agent:assign-next-todo", "Assigned next pending todo")
        } else {
            failure("agent:assign-next-todo", "No todo could be assigned")
        }
}

/**
 * Assigns all pending todos up to the available worker capacity.
 */
@Component
class AgentAssignAllTodosTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-all-todos"),
            name = ToolId("agent:assign-all-todos").toFunctionName(),
            description = "Assign all pending todos up to the available worker capacity.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val count = agentManager.assignAllPendingTodos()
        return success("agent:assign-all-todos", "Assigned $count todo(s)")
    }
}
