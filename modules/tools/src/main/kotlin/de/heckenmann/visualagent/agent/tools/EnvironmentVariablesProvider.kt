package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component

/** Reads the environment of the Visual Agent server process. */
fun interface EnvironmentVariablesProvider {
    /** Return a snapshot of process environment names and values. */
    fun snapshot(): Map<String, String>
}

/** Reads process environment values through the Java runtime API. */
@Component
class JvmEnvironmentVariablesProvider : EnvironmentVariablesProvider {
    override fun snapshot(): Map<String, String> = System.getenv().toMap()
}
