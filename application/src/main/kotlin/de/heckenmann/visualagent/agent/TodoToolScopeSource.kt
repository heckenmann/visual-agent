package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/** Creates a fresh Spring-owned prototype for each autonomous worker attempt. */
@Service
class TodoToolScopeSource private constructor(
    private val createScope: () -> ToolExecutionScope,
) {
    /** Uses Spring's prototype provider; no execution state is shared between attempts. */
    @Autowired
    constructor(provider: ObjectProvider<ToolExecutionScope>) : this({ provider.getObject() })

    /** Isolated constructor for non-Spring test fixtures. */
    internal constructor() : this({ ToolExecutionScope() })

    /** Returns one independently owned attempt scope. */
    fun create(): ToolExecutionScope = createScope()
}
