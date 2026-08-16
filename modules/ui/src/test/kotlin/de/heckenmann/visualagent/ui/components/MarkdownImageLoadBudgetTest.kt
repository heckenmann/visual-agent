package de.heckenmann.visualagent.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies aggregate Markdown image resource limits. */
class MarkdownImageLoadBudgetTest {
    @Test
    fun `limits concurrent image work`() {
        val budget = MarkdownImageLoadBudget(maxConcurrent = 1, maxBytes = 100, maxPixels = 100)
        val first = assertNotNull(budget.tryAcquire())

        assertNull(budget.tryAcquire())
        first.release()
        assertNotNull(budget.tryAcquire()).release()
    }

    @Test
    fun `limits aggregate encoded bytes`() {
        val budget = MarkdownImageLoadBudget(maxConcurrent = 2, maxBytes = 10, maxPixels = 100)
        val first = assertNotNull(budget.tryReserve(encodedBytes = 6, decodedPixels = 1))

        assertNull(budget.tryReserve(encodedBytes = 5, decodedPixels = 1))
        first.release()
        assertNotNull(budget.tryReserve(encodedBytes = 5, decodedPixels = 1))
    }

    @Test
    fun `limits aggregate decoded pixels discovered after decoding`() {
        val budget = MarkdownImageLoadBudget(maxConcurrent = 2, maxBytes = 100, maxPixels = 10)
        val first = assertNotNull(budget.tryReserve(encodedBytes = 1, decodedPixels = 0))

        assertTrue(first.ensurePixels(10))
        assertFalse(first.ensurePixels(11))
        assertNull(budget.tryReserve(encodedBytes = 1, decodedPixels = 1))
        first.release()
        assertNotNull(budget.tryReserve(encodedBytes = 1, decodedPixels = 1))
    }
}
