package de.heckenmann.visualagent.task

/** Translates only tool identity fields, preserving task prose, paths, actions and revisions. */
fun TaskContract.mapToolNames(name: (String) -> String): TaskContract =
    copy(
        requiredOperations = requiredOperations.map { it.copy(tool = name(it.tool)) },
        criteria = criteria.map { it.copy(executionTool = it.executionTool?.let(name)) },
        policy =
            policy.copy(
                tools = policy.tools?.map(name),
                actions = policy.actions.mapKeys { name(it.key) },
            ),
    )

/** Translates persisted evidence and its contract snapshot for a model-facing response. */
fun TaskAttemptResult.mapToolNames(name: (String) -> String): TaskAttemptResult =
    copy(
        contractSnapshot = contractSnapshot?.mapToolNames(name),
        toolEvidence = toolEvidence.map { it.copy(tool = name(it.tool)) },
    )
