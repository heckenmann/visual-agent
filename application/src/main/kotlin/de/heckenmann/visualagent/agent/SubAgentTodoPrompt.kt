package de.heckenmann.visualagent.agent

/** Builds the unchanged worker-only tool and lifecycle instructions. */
internal fun todoWorkerMessages(
    agent: SubAgent,
    description: String,
    enabledTools: Set<ToolId>,
): List<Message> =
    listOf(
        Message(
            "system",
            buildString {
                append("Worker policy worker/1. You are ${agent.name}. Your role is ${agent.role}.")
                append(
                    de.heckenmann.visualagent.task.TaskPrompts
                        .specialist(agent.config.templateName.orEmpty()),
                )
                append(" The main agent and orchestrator control the todo lifecycle.")
                append(
                    " As a worker, do not add, update, complete, " +
                        "cancel, start, stop, remove, or reorder todos.",
                )
                if (enabledTools.any { it.value == "todos" }) {
                    append(" You may inspect todos and stored results with read-only `todos` actions when clarification is needed.")
                } else {
                    append(" If the task becomes unclear, report a blocker and request clarification.")
                }
                append(
                    " Report a concise result and next steps; the orchestrator persists the result " +
                        "and decides the final status.",
                )
                if (enabledTools.any { it.value == "javascript:execute" }) {
                    append(
                        " Use the available JavaScript function for complex deterministic logic and bulk processing " +
                            "of many elements " +
                            "(mapping, filtering, transforming, deduplicating, sorting, or aggregating records), " +
                            "or large CSV/Markdown assembly; call only enabled tools through " +
                            "await tools.call(name, arguments), use workspace.write({path, content}) " +
                            "for generated text that must be persisted, workspace.read({path}) to read " +
                            "text, and workspace.delete({path}) to remove a file. Existing JavaScript files " +
                            "can be executed by passing their relative path to that function; " +
                            "return the complete final value. " +
                            "If execution returns an error, inspect it, correct the source or arguments, " +
                            "and retry without repeating the unchanged failure. The sandbox has no direct " +
                            "host, filesystem, network, process, or credential access.",
                    )
                }
                if (enabledTools.any { it.value == "skills" }) {
                    append(
                        " Search the skills catalog before expensive or repetitive work with skills search, " +
                            "read a matching skill before relying on it, and save only stable reusable Markdown " +
                            "results with skills create or update. Never store secrets, PII, transient progress, " +
                            "or raw provider responses. Skills are database records, never workspace files: " +
                            "do not create SKILL.md or another skill document with file-editing, JavaScript, " +
                            "or terminal functions. Use only the supplied function schemas for nested calls.",
                    )
                } else {
                    append(
                        " Skill requests are handled by the main agent's skills tool. Do not create, write, " +
                            "or modify SKILL.md or any other skill document in the workspace; report the " +
                            "request to the orchestrator instead.",
                    )
                }
            },
        ),
        Message("user", description),
    )
