package de.heckenmann.visualagent.agent.codex

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class PackageManagerCodexCliReleaseVersionSourceTest {
    private val source = PackageManagerCodexCliReleaseVersionSource(CodexCliProcessFactory())

    @Test
    fun `parses npm version response`() {
        assertEquals("0.146.0", source.versionFromJson("[\"0.146.0\"]"))
    }

    @Test
    fun `parses yarn version response`() {
        assertEquals("0.146.0", source.versionFromJson("{\"type\":\"inspect\",\"data\":\"0.146.0\"}"))
    }

    @Test
    fun `rejects malformed registry response`() {
        assertEquals(null, source.versionFromJson("not json"))
    }
}
