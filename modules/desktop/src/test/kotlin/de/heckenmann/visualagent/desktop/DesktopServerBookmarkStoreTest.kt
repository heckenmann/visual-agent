package de.heckenmann.visualagent.desktop

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies client-local Visual Agent server bookmark persistence and validation. */
class DesktopServerBookmarkStoreTest {
    @Test
    fun `missing bookmark file loads local selection without creating storage`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val file = directory.resolve("startup-servers.json")

        val loaded = DesktopServerBookmarkStore(file).load()

        assertEquals(DesktopServerBookmarkState(), assertIs<DesktopServerBookmarkLoadResult.Loaded>(loaded).state)
        assertTrue(Files.notExists(file))
    }

    @Test
    fun `save normalizes endpoint and preserves selected remote bookmark`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val file = directory.resolve("startup-servers.json")
        val bookmark = bookmark(name = "  Home server  ", endpoint = "grpcs://SERVER.EXAMPLE:7443")
        val state = DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark), lastSelectedVisualAgentServerId = bookmark.id)

        DesktopServerBookmarkStore(file).save(state)

        val loaded = assertIs<DesktopServerBookmarkLoadResult.Loaded>(DesktopServerBookmarkStore(file).load()).state
        assertEquals("Home server", loaded.visualAgentServerBookmarks.single().name)
        assertEquals("grpcs://server.example:7443", loaded.visualAgentServerBookmarks.single().visualAgentServerEndpoint)
        assertEquals(bookmark.id, loaded.lastSelectedVisualAgentServerId)
    }

    @Test
    fun `duplicate normalized endpoints are rejected before overwriting the stored file`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val file = directory.resolve("startup-servers.json")
        val store = DesktopServerBookmarkStore(file)
        val initial = DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark(endpoint = "grpcs://one.example:7443")))
        store.save(initial)

        val failure =
            runCatching {
                store.save(
                    DesktopServerBookmarkState(
                        visualAgentServerBookmarks =
                            listOf(
                                bookmark(endpoint = "grpcs://SERVER.EXAMPLE:7443"),
                                bookmark(endpoint = "grpcs://server.example:7443"),
                            ),
                    ),
                )
            }.exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertEquals(initial, assertIs<DesktopServerBookmarkLoadResult.Loaded>(store.load()).state)
    }

    @Test
    fun `invalid persisted file is preserved and reported without removing local fallback`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val file = directory.resolve("startup-servers.json")
        Files.writeString(file, "not json")

        val result = DesktopServerBookmarkStore(file).load()

        assertIs<DesktopServerBookmarkLoadResult.Invalid>(result)
        assertEquals("not json", Files.readString(file))
    }

    @Test
    fun `valid legacy bookmark file is copied to the client config root`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val primary = directory.resolve("config/startup-servers.json")
        val legacy = directory.resolve("legacy/startup-servers.json")
        val bookmark = bookmark(name = "Migrated")
        val state = DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark))
        val legacyStore = DesktopServerBookmarkStore(legacy, legacyStorageFile = null)
        legacyStore.save(state)

        val loaded = DesktopServerBookmarkStore(primary, legacy).load()

        assertEquals(state, assertIs<DesktopServerBookmarkLoadResult.Loaded>(loaded).state)
        assertTrue(Files.exists(primary))
        assertTrue(Files.exists(legacy))
    }

    @Test
    fun `existing client config wins over legacy bookmarks without being overwritten`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val primary = directory.resolve("config/startup-servers.json")
        val legacy = directory.resolve("legacy/startup-servers.json")
        val primaryState = DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark(name = "Current")))
        val legacyState = DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark(name = "Legacy")))
        DesktopServerBookmarkStore(primary, legacy, Json { prettyPrint = true }).save(primaryState)
        DesktopServerBookmarkStore(legacy, legacyStorageFile = null, json = Json { prettyPrint = true }).save(legacyState)

        val loaded = DesktopServerBookmarkStore(primary, legacy).load()

        assertEquals(primaryState, assertIs<DesktopServerBookmarkLoadResult.Loaded>(loaded).state)
        assertEquals(legacyState, assertIs<DesktopServerBookmarkLoadResult.Loaded>(DesktopServerBookmarkStore(legacy).load()).state)
    }

    @Test
    fun `legacy publication never replaces a concurrently created destination`() {
        val directory = Files.createTempDirectory("visual-agent-bookmarks")
        val source = directory.resolve("legacy.tmp")
        val target = directory.resolve("startup-servers.json")
        Files.writeString(source, "legacy")
        Files.writeString(target, "current")

        assertTrue(!copyFileWithoutReplacement(source, target))
        assertEquals("current", Files.readString(target))
    }

    @Test
    fun `client config root override resolves without consulting the working directory`() {
        val configuredRoot = Files.createTempDirectory("visual-agent-client-config")

        assertEquals(
            configuredRoot.resolve("startup-servers.json").toAbsolutePath().normalize(),
            ClientConfigPathResolver.resolveBookmarkFile(configuredRoot.toString()),
        )
    }

    private fun bookmark(
        name: String = "Server",
        endpoint: String = "grpcs://server.example:7443",
    ): DesktopServerBookmark =
        DesktopServerBookmark(
            id = UUID.randomUUID().toString(),
            name = name,
            visualAgentServerEndpoint = endpoint,
        )
}
