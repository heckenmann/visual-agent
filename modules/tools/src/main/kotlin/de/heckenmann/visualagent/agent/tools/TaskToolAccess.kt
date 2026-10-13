package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.task.TaskContractCodec
import de.heckenmann.visualagent.task.TaskPolicy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Trusted server-created authority inherited by direct, nested, batch and detached calls. */
class TaskToolAccess(
    private val policy: TaskPolicy,
    private val enabledNow: () -> Set<String>,
    private val current: () -> Boolean,
    private val resourceBoundary: (String) -> Boolean = { true },
) {
    /** Checks current ownership and the intersection of global, agent and task permissions. */
    fun requireAllowed(
        tool: String,
        input: JsonObject,
    ) {
        check(current()) { "TASK_SCOPE_REVOKED: The task snapshot is no longer current" }
        require(tool in enabledNow() && (policy.tools?.let { tool in it } ?: true)) { "TASK_SCOPE_DENIED: Tool is outside this task" }
        val action = input["action"]?.jsonPrimitive?.contentOrNull ?: defaultAction(tool)
        policy.actions[tool]?.let { require(action in it) { "TASK_SCOPE_DENIED: Action is outside this task" } }
        val resources = policy.resources ?: return
        require(supportsResourceScope(tool, resources)) {
            "TASK_SCOPE_DENIED: Tool cannot enforce this task's filesystem resource boundary"
        }
        if (tool != "workspace:file") return
        require(action !in setOf("list", "listRoots", "search", "sync", "glob", "grep", "renderPdfPage")) {
            "TASK_SCOPE_DENIED: Use an explicit path operation that preserves resource boundaries"
        }
        require(
            listOf("rootId", "sourceRootId", "targetRootId").all {
                input[it]?.jsonPrimitive?.contentOrNull in setOf(null, "workspace")
            },
        ) { "TASK_SCOPE_DENIED: External roots cannot be used in a scoped task" }
        val paths =
            if (action == "createDirectory") {
                val parent =
                    input["parentDirectory"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                        .trim()
                        .trim('/')
                val name = requireNotNull(input["name"]?.jsonPrimitive?.contentOrNull).trim()
                require('/' !in name && '\\' !in name) { "TASK_SCOPE_DENIED: A single directory name is required" }
                listOf(if (parent.isEmpty()) name else "$parent/$name")
            } else {
                listOf("path", "sourcePath", "targetPath", "directory", "parentDirectory").mapNotNull {
                    input[it]?.jsonPrimitive?.contentOrNull
                }
            }
        require(paths.isNotEmpty()) { "TASK_SCOPE_DENIED: A workspace path is required for resource-scoped calls" }
        paths.forEach { path ->
            TaskContractCodec.validatePath(path)
            require(resourceBoundary(path)) { "TASK_SCOPE_DENIED: Resource path crosses the workspace boundary" }
            require(
                resources.any { allowed ->
                    path == allowed || path.startsWith("$allowed/")
                },
            ) { "TASK_SCOPE_DENIED: Resource is outside this task" }
        }
        require(input["id"] == null) { "TASK_SCOPE_DENIED: Use an explicit path instead of an opaque file identifier" }
    }

    companion object {
        /**
         * A scoped task may only use file operations with checked paths or tools that cannot
         * directly access the local filesystem. Unknown tools fail closed.
         */
        private val RESOURCE_SCOPED_SAFE_TOOLS =
            setOf(
                "workspace:file",
                "system:time",
                "sleep",
                "network:dns",
                "network:reverse-dns",
                "network:tcp",
                "network:ping",
                "network:traceroute",
                "network:http",
                "network:tls",
            )

        /** Returns whether this tool can safely execute under the given resource restrictions. */
        fun supportsResourceScope(
            tool: String,
            resources: List<String>?,
        ): Boolean = resources == null || tool in RESOURCE_SCOPED_SAFE_TOOLS
    }

    private fun defaultAction(tool: String): String? =
        when (tool) {
            "workspace:file", "todos" -> "list"
            else -> null
        }
}
