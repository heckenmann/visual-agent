package de.heckenmann.visualagent.update

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {
    @Test
    fun `compares stable versions numerically`() {
        assertTrue(SemanticVersion.parse("v1.10.0")!! > SemanticVersion.parse("1.9.9")!!)
    }

    @Test
    fun `orders prerelease identifiers according to semver`() {
        val alpha = SemanticVersion.parse("1.0.0-alpha.1")!!
        val beta = SemanticVersion.parse("1.0.0-beta.1")!!
        val stable = SemanticVersion.parse("1.0.0")!!

        assertTrue(alpha < beta)
        assertTrue(beta < stable)
    }

    @Test
    fun `ignores build metadata and rejects unsupported versions`() {
        assertEquals(SemanticVersion.parse("1.2.3"), SemanticVersion.parse("v1.2.3+build.7"))
        assertNull(SemanticVersion.parse("release-1.2.3"))
    }
}
