package de.heckenmann.visualagent.release

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

/** Isolates the real release shell scripts behind a local, non-networked GitHub CLI. */
internal class ReleaseWorkflowFixture(
    val root: Path,
) {
    val repository: Path = Path.of(System.getProperty("release.tools.repository"))
    val assets: Path = Files.createDirectory(root.resolve("assets"))

    init {
        val script = javaClass.getResourceAsStream("/release/gh-fixture.sh")!!.use { it.readBytes() }
        val gh = Files.write(root.resolve("gh"), script)
        check(gh.toFile().setExecutable(true))
        ReleaseAssets.requiredNames.forEach { name ->
            val target = assets.resolve(name)
            if (name.endsWith(".zip")) {
                ZipOutputStream(Files.newOutputStream(target)).use {
                    it.putNextEntry(ZipEntry("README.txt"))
                    it.write("Recipient materials".toByteArray())
                    it.closeEntry()
                }
            } else {
                Files.writeString(target, "package")
            }
        }
        val pages =
            buildJsonArray {
                add(
                    buildJsonArray {
                        ReleaseAssets.requiredNames.forEach { name ->
                            val file = assets.resolve(name)
                            add(
                                buildJsonObject {
                                    put("name", name)
                                    put("state", "uploaded")
                                    put("size", Files.size(file))
                                    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                                    put("digest", "sha256:" + digest.joinToString("") { "%02x".format(it) })
                                },
                            )
                        }
                    },
                )
            }
        Files.writeString(root.resolve("uploaded.json"), pages.toString())
    }

    fun run(
        script: String = "attach-release-draft.sh",
        vararg overrides: Pair<String, String>,
    ): Pair<Int, String> {
        val environment =
            mutableMapOf(
                "PATH" to "$root${java.io.File.pathSeparator}${System.getenv("PATH")}",
                "FIXTURE" to root.toString(),
                "GH_REPO" to "owner/repo",
                "RELEASE_TAG" to "v1.2.3",
                "SOURCE_COMMIT" to "a".repeat(40),
                "TAG_COMMIT" to "a".repeat(40),
                "RELATION" to "ahead",
                "EXISTING" to "none",
                "PREVIEW" to "false",
                "JAVA_HOME" to System.getProperty("java.home"),
            )
        environment.putAll(overrides)
        if ("GITHUB_EVENT_PATH" !in environment) {
            val event =
                buildJsonObject {
                    put("ref", "refs/tags/${environment.getValue("RELEASE_TAG")}")
                    put("created", true)
                    put("forced", false)
                    put("deleted", false)
                }
            val path = Files.writeString(root.resolve("push-event.json"), event.toString())
            environment["GITHUB_EVENT_PATH"] = path.toString()
        }
        val args = mutableListOf("bash", repository.resolve("scripts/$script").toString())
        if (script == "attach-release-draft.sh") args.add(assets.toString())
        val output = root.resolve("command.log")
        val process =
            ProcessBuilder(args)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .apply { environment().putAll(environment) }
                .start()
        process.outputStream.close()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Release script timed out")
            return process.exitValue() to Files.readString(output)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }

    fun log(): String = root.resolve("log").let { if (Files.exists(it)) Files.readString(it) else "" }
}
