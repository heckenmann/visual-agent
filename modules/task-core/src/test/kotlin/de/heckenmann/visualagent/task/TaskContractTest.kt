package de.heckenmann.visualagent.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TaskContractTest {
    private val contract =
        TaskContract(
            objective = "Create a grounded answer",
            inputs = listOf(TaskInput("source", path = "sources/input.txt")),
            deliverables = listOf(TaskDeliverable("answer", "Answer", "results/answer.txt")),
            criteria = listOf(TaskCriterion("quote", "Exact quote", "answer", TaskCheckMethod.SOURCE_QUOTE, "Fact", "source")),
            policy = TaskPolicy(tools = emptyList(), resources = emptyList()),
        )

    @Test
    fun `contracts round trip without losing empty deny scopes and legacy stays absent`() {
        assertEquals(contract, TaskContractCodec.decode(TaskContractCodec.encode(contract)))
        assertNull(TaskContractCodec.decode(null))
    }

    @Test
    fun `rejects malformed references and unsafe resource paths`() {
        listOf("../escape", "/tmp/a", "a/../b", "a\\b", "", "a//b").forEach { path ->
            assertFailsWith<IllegalArgumentException> { TaskContractCodec.validatePath(path) }
        }
        assertFailsWith<IllegalArgumentException> {
            TaskContractCodec.validate(contract.copy(criteria = listOf(TaskCriterion("x", "Check", "missing"))))
        }
        assertFailsWith<IllegalArgumentException> { TaskContractCodec.validate(contract.copy(version = 2)) }
        assertFailsWith<IllegalArgumentException> {
            TaskContractCodec.validate(contract.copy(inputs = listOf(TaskInput("x", sourceUrl = "https://user:password@example.com"))))
        }
    }

    @Test
    fun `manual checks without an inspectable artifact fail validation`() {
        val manualWithoutArtifact = TaskCriterion("review", "Review the result", method = TaskCheckMethod.MANUAL)
        val invalid = contract.copy(criteria = listOf(manualWithoutArtifact))
        val error = assertFailsWith<IllegalArgumentException> { TaskContractCodec.validate(invalid) }
        kotlin.test.assertContains(error.message.orEmpty(), "declared artifact")
        val valid = invalid.copy(criteria = listOf(manualWithoutArtifact.copy(artifactId = "answer")))
        assertEquals(valid, TaskContractCodec.decode(TaskContractCodec.encode(valid)))
    }

    @Test
    fun `worker cannot forge provenance or claim an undeclared artifact`() {
        assertFailsWith<Exception> { WorkerHandoff.parse("""{"summary":"Done","outcome":"PRODUCED","agentId":"trusted"}""", contract) }
        assertFailsWith<IllegalArgumentException> {
            WorkerHandoff.parse("""{"summary":"Done","outcome":"PRODUCED","artifacts":[{"id":"answer","path":"other.txt"}]}""", contract)
        }
        assertEquals(
            TaskOutcome.BLOCKED,
            WorkerHandoff.parse("""{"summary":"Need permission","outcome":"BLOCKED","blockers":["Missing access"]}""", contract).outcome,
        )
    }

    @Test
    fun `decomposition cannot silently drop a parent requirement or broaden policy`() {
        val parent =
            contract.copy(
                deliverables = contract.deliverables + TaskDeliverable("other", "Separate output", "other.txt"),
                criteria = contract.criteria + TaskCriterion("second", "Another check", "other"),
            )
        assertFailsWith<IllegalArgumentException> {
            TaskDecomposition(listOf(TaskChild("First", listOf("quote")), TaskChild("Second", listOf("quote")))).contracts(parent, "parent")
        }
        val children =
            TaskDecomposition(
                listOf(TaskChild("First", listOf("quote")), TaskChild("Second", listOf("second"))),
                independent = true,
            ).contracts(parent, "parent")
        assertEquals(parent.policy, children.first().policy)
        assertEquals("parent", children.first().parentTodoId)
        assertEquals(setOf("quote", "second"), children.flatMap { it.parentCriterionIds }.toSet())
    }

    @Test
    fun `different artifact identities cannot send concurrent children to the same output path`() {
        val parent =
            contract.copy(
                deliverables = contract.deliverables + TaskDeliverable("alias", "Same file", "results/answer.txt"),
                criteria = contract.criteria + TaskCriterion("second", "Another check", "alias"),
            )
        TaskContractCodec.validate(parent)
        val proposal =
            TaskDecomposition(
                listOf(TaskChild("First package", listOf("quote")), TaskChild("Second package", listOf("second"))),
                independent = true,
            )
        assertFailsWith<IllegalArgumentException> { proposal.contracts(parent, "parent") }
    }

    @Test
    fun `multiple references to one output remain valid when owned by the same child`() {
        val parent =
            contract.copy(
                deliverables =
                    contract.deliverables +
                        listOf(
                            TaskDeliverable("alias", "Same file", "results/answer.txt"),
                            TaskDeliverable("other", "Separate file", "other.txt"),
                        ),
                criteria =
                    contract.criteria +
                        listOf(
                            TaskCriterion("alias-check", "Check the same file", "alias"),
                            TaskCriterion("other-check", "Check the separate file", "other"),
                        ),
            )
        val children =
            TaskDecomposition(
                listOf(TaskChild("First package", listOf("quote", "alias-check")), TaskChild("Second package", listOf("other-check"))),
                independent = true,
            ).contracts(parent, "parent")
        assertEquals(
            setOf("answer", "alias"),
            children
                .first()
                .deliverables
                .map { it.id }
                .toSet(),
        )
    }
}
