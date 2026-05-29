package de.heckenmann.visualagent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VisualAgentApplication

fun main(args: Array<String>) {
    runApplication<VisualAgentApplication>(*args)
}
