package de.heckenmann.visualagent.config

import net.harawata.appdirs.impl.UnixAppDirs
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies server data path precedence and platform-default shape. */
class ServerDataPathResolverTest {
    @Test
    fun `explicit database path has precedence over server data root`() {
        val environment =
            MockEnvironment()
                .withProperty("visual-agent.db.path", "/tmp/explicit/agent.db")
                .withProperty("visual-agent.server.data-root", "/tmp/server-root")

        assertEquals("/tmp/explicit/agent.db", ServerDataPathResolver.databasePath(environment))
    }

    @Test
    fun `explicit database path resolves its parent as the server data root`() {
        val environment = MockEnvironment().withProperty("visual-agent.db.path", "/tmp/explicit/agent.db")

        assertEquals(Path.of("/tmp/explicit"), ServerDataPathResolver.serverDataRoot(environment))
    }

    @Test
    fun `configured server data root is exposed independently from database naming`() {
        val serverRoot = Files.createTempDirectory("visual-agent-server-root")
        val environment = MockEnvironment().withProperty("visual-agent.server.data-root", serverRoot.toString())

        assertEquals(serverRoot, ServerDataPathResolver.serverDataRoot(environment))
    }

    @Test
    fun `server data root determines database file when no database override exists`() {
        val serverRoot = Files.createTempDirectory("visual-agent-server-root")
        val environment = MockEnvironment().withProperty("visual-agent.server.data-root", serverRoot.toString())

        assertEquals(serverRoot.resolve("visual-agent.db").toString(), ServerDataPathResolver.databasePath(environment))
    }

    @Test
    fun `relative explicit paths are rejected instead of becoming CWD dependent`() {
        assertFailsWith<IllegalArgumentException> {
            ServerDataPathResolver.databasePath(MockEnvironment().withProperty("visual-agent.db.path", "data/agent.db"))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerDataPathResolver.databasePath(
                MockEnvironment().withProperty("visual-agent.server.data-root", "data/server"),
            )
        }
    }

    @Test
    fun `in-memory sqlite override remains available for tests`() {
        assertEquals(
            "jdbc:sqlite::memory:",
            ServerDataPathResolver.databasePath(
                MockEnvironment().withProperty("visual-agent.db.path", "jdbc:sqlite::memory:"),
            ),
        )
    }

    @Test
    fun `default server data root is absolute and isolated below a server directory`() {
        val root = ServerDataPathResolver.defaultServerDataRoot()

        assertTrue(root.isAbsolute)
        assertEquals("server", root.fileName.toString())
    }

    @Test
    fun `linux appdirs honors XDG data home`() {
        val xdgDataHome = Files.createTempDirectory("visual-agent-xdg-data")
        val appDirs = UnixAppDirs(mapOf("XDG_DATA_HOME" to xdgDataHome.toString(), "HOME" to "/unused"))

        assertEquals(
            xdgDataHome.resolve("Visual Agent/server").toAbsolutePath().normalize(),
            ServerDataPathResolver.defaultServerDataRoot(appDirs),
        )
    }

    @Test
    fun `linux appdirs falls back to home local share when XDG data home is missing`() {
        val appDirs = UnixAppDirs(emptyMap())
        val home = Path.of(System.getProperty("user.home"))

        assertEquals(
            home.resolve(".local/share/Visual Agent/server").toAbsolutePath().normalize(),
            ServerDataPathResolver.defaultServerDataRoot(appDirs),
        )
    }

    @Test
    fun `windows appdirs root gets a dedicated server namespace`() {
        assertEquals(
            Path.of("/windows/local/Visual Agent/server").toAbsolutePath().normalize(),
            ServerDataPathResolver.serverDataRoot("/windows/local/Visual Agent"),
        )
    }

    @Test
    fun `macos appdirs root gets a dedicated server namespace`() {
        assertEquals(
            Path.of("/Users/alice/Library/Application Support/Visual Agent/server").toAbsolutePath().normalize(),
            ServerDataPathResolver.serverDataRoot("/Users/alice/Library/Application Support/Visual Agent"),
        )
    }

    @Test
    fun `default path does not implicitly reuse repository local legacy data`() {
        val legacyPath =
            Path
                .of(System.getProperty("user.dir"))
                .resolve("data/visual-agent.db")
                .toAbsolutePath()
                .normalize()

        assertNotEquals(legacyPath, Path.of(ServerDataPathResolver.databasePath(MockEnvironment())))
    }
}
