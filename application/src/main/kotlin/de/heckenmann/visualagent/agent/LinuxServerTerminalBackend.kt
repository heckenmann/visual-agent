package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.TerminalSandboxExecution
import de.heckenmann.visualagent.agent.tools.TerminalSandboxRequest
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.workspace.ServerTerminalDirectory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs a server terminal command in a Bubblewrap filesystem, process and network namespace. */
@Component
internal class LinuxServerTerminalBackend : ServerTerminalBackend {
    override fun start(
        request: TerminalSandboxRequest,
        directory: ServerTerminalDirectory,
        context: Map<String, Any>,
    ): TerminalSandboxExecution? {
        if (!hasRuntime()) return null
        val runtime = runtimeMounts() ?: return null
        val workspace = "/workspace"
        val workingDirectory = Path.of(workspace).resolve(directory.root.relativize(directory.workingDirectory)).toString()
        val command =
            listOf(
                "/usr/bin/bash",
                "-c",
                PIN_DIRECTORY,
                "visual-agent-terminal",
                directory.root.toString(),
                directory.identity,
                BUBBLEWRAP,
                "--unshare-all",
                "--unshare-user",
                "--disable-userns",
                "--die-with-parent",
                "--new-session",
                "--cap-drop",
                "ALL",
                "--clearenv",
                "--setenv",
                "PATH",
                "/usr/bin",
                "--setenv",
                "HOME",
                "/tmp",
            ) + runtime +
                listOf(
                    "--symlink",
                    "usr/bin",
                    "/bin",
                    "--symlink",
                    "usr/lib",
                    "/lib",
                    "--symlink",
                    "usr/lib",
                    "/lib64",
                    "--proc",
                    "/proc",
                    "--dev",
                    "/dev",
                    "--tmpfs",
                    "/tmp",
                    if (directory.mode == DirectoryAccessMode.READ_WRITE) "--bind-fd" else "--ro-bind-fd",
                    "9",
                    workspace,
                    "--chdir",
                    workingDirectory,
                    "--seccomp",
                    "0",
                    "--",
                    "/usr/bin/bash",
                    "-c",
                    request.command,
                )
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().clear()
        val process = builder.start()
        val execution = BubblewrapExecution(process)
        try {
            // Bubblewrap consumes the filter before executing Bash. EOF leaves terminal input closed.
            process.outputStream.use { it.write(TerminalSeccompFilter.x86Filter()) }
            return execution
        } catch (failure: Exception) {
            runCatching { execution.cancel() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun hasRuntime(): Boolean =
        System.getProperty("os.name") == "Linux" &&
            System.getProperty("os.arch") in setOf("amd64", "x86_64") &&
            Files.isExecutable(Path.of(BUBBLEWRAP)) &&
            runtimeMounts() != null

    /** Runs a directory listing under the same namespaces, mount setup and seccomp filter as a real command. */
    fun supportsPlatform(): Boolean =
        runCatching {
            val root = Path.of("/usr/bin").toRealPath()
            val request = TerminalSandboxRequest(de.heckenmann.visualagent.agent.tools.TerminalHost.SERVER, "ls -d .", null, null)
            val execution =
                start(request, ServerTerminalDirectory(root, root, DirectoryAccessMode.READ_ONLY), emptyMap())
                    as? BubblewrapExecution ?: return false
            try {
                execution.exitsSuccessfullyWithin(5)
            } finally {
                execution.cancel()
                execution.output.close()
            }
        }.getOrDefault(false)

    private fun runtimeMounts(): List<String>? {
        val libraryDirectories = listOf(Path.of("/usr/lib/x86_64-linux-gnu"), Path.of("/usr/lib"))
        val libraries =
            listOf(
                "ld-linux-x86-64.so.2",
                "libc.so.6",
                "libtinfo.so.6",
                "libreadline.so.8",
                "libncursesw.so.6",
                "libcap.so.2",
                "libselinux.so.1",
                "libpcre2-8.so.0",
            )
        val mounts = mutableListOf<String>()
        for (program in listOf("bash", "cat", "touch", "ls", "sleep")) {
            val source = Path.of("/usr/bin", program)
            if (!Files.isExecutable(source)) return null
            mounts.addAll(listOf("--ro-bind", source.toString(), source.toString()))
        }
        for (library in libraries) {
            val source = libraryDirectories.map { it.resolve(library) }.firstOrNull(Files::isRegularFile)
            if (source == null) {
                if (library == "ld-linux-x86-64.so.2" || library == "libc.so.6") return null
                continue
            }
            mounts.addAll(listOf("--ro-bind", source.toString(), "/usr/lib/$library"))
        }
        return mounts
    }

    private class BubblewrapExecution(
        private val process: Process,
    ) : TerminalSandboxExecution {
        override val output get() = process.inputStream

        override fun awaitExit(): Int = process.waitFor()

        fun exitsSuccessfullyWithin(seconds: Long): Boolean = process.waitFor(seconds, TimeUnit.SECONDS) && process.exitValue() == 0

        override fun cancel() {
            // Bubblewrap links its PID namespace lifetime to the supervisor.
            process.destroyForcibly()
            check(process.waitFor(5, TimeUnit.SECONDS)) { "Bubblewrap did not stop after cancellation." }
        }
    }
}

private const val BUBBLEWRAP = "/usr/bin/bwrap"

// Bubblewrap mounts the open descriptor, preserving identity across later pathname changes.
private val PIN_DIRECTORY =
    """
    set -eu
    exec 9< "$1"
    if ! test -d /proc/self/fd/9 || ! test "$(/usr/bin/readlink -- /proc/self/fd/9)" = "$1" ||
        ! test "$(/usr/bin/stat -Lc %d:%i -- /proc/self/fd/9)" = "$2"; then
        printf "%s\n" "The authorized terminal directory changed before launch." >&2
        exit 1
    fi
    shift 2
    exec "$@"
    """.trimIndent()
