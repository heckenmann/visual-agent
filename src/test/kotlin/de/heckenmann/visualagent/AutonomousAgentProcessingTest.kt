package de.heckenmann.visualagent

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["visual-agent.ui.enabled=false"])
@Disabled("Exploratory autonomous-processing test; depends on local LLM availability and is not part of the core regression suite")
class AutonomousAgentProcessingTest {
    @Autowired
    private lateinit var agentManager: AgentManager

    @Test
    fun testAutonomousProcessing() =
        runBlocking {
            println("\n🤖 Starting autonomous SubAgent processing test...\n")

            // Seed tasks
            agentManager.seedUxTodos()
            val initialCount = agentManager.todoManager.getAll().size
            println("✓ Seeded $initialCount UX tasks")

            // Start processing
            agentManager.startAutonomousProcessing(seed = false)
            println("✓ Started autonomous processing\n")

            // Monitor for 60 seconds
            var lastReport = 0
            repeat(30) {
                // 30 * 2s = 60 seconds
                delay(2000)
                val all = agentManager.todoManager.getAll()
                val completed = all.count { it.status == TodoStatus.COMPLETED }
                val cancelled = all.count { it.status == TodoStatus.CANCELLED }
                val pending = all.count { it.status == TodoStatus.PENDING }
                val inProgress = all.count { it.status == TodoStatus.IN_PROGRESS }

                if (completed + cancelled > lastReport) {
                    lastReport = completed + cancelled
                    println("  Processed: ${completed + cancelled}/$initialCount | Pending: $pending | In Progress: $inProgress")
                }

                if (pending == 0 && inProgress == 0) {
                    println("\n✓ All tasks processed!")
                    return@runBlocking
                }
            }

            println("\n✓ Test completed. Final state:")
            val all = agentManager.todoManager.getAll()
            println("  Completed: ${all.count { it.status == TodoStatus.COMPLETED }}")
            println("  Cancelled: ${all.count { it.status == TodoStatus.CANCELLED }}")
        }
}
