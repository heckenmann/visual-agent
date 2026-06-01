package de.heckenmann.visualagent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Represents VisualAgentApplication.
 */
@SpringBootApplication
class VisualAgentApplication

/**
 * Executes main.
 */
fun main(args: Array<String>) {
    runApplication<VisualAgentApplication>(*args)
}
