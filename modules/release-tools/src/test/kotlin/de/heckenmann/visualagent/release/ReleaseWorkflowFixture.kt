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

    fun preparePublishedRecovery() {
        val artifacts =
            buildJsonArray {
                (ReleaseAssets.requiredNames.sorted() + "release-verification-tools").forEachIndexed { index, file ->
                    val id = index + 1
                    val name = if (file == "release-verification-tools") file else file.substringBeforeLast('.')
                    ZipOutputStream(Files.newOutputStream(root.resolve("$id.zip"))).use { zip ->
                        zip.putNextEntry(ZipEntry(file))
                        if (file != "release-verification-tools") Files.copy(assets.resolve(file), zip)
                        zip.closeEntry()
                    }
                    add(
                        buildJsonObject {
                            put("id", id)
                            put("name", name)
                            put("expired", false)
                        },
                    )
                }
            }
        Files.writeString(
            root.resolve("artifacts.json"),
            buildJsonArray { add(buildJsonObject { put("artifacts", artifacts) }) }.toString(),
        )
        Files.writeString(
            root.resolve("source-run.json"),
            buildJsonObject {
                put("workflow_id", 42)
                put("run_attempt", 1)
                put("event", "push")
                put("status", "completed")
                put("conclusion", "failure")
                put("head_branch", "v1.2.3")
                put("head_sha", "a".repeat(40))
            }.toString(),
        )
        val passedJobs =
            listOf(
                "Verify release source",
                "Package linux-deb",
                "Package linux-rpm",
                "Package linux-appimage",
                "Package linux-x64-jar",
                "Package macos-arm64-jar",
                "Package macos-x64-jar",
                "Package windows-x64-jar",
                "Package macos-arm64",
                "Package macos-x64",
                "Package windows",
                "Package legal and source review materials",
                "Smoke Linux executable JAR",
                "Smoke macos-arm64-jar jar",
                "Smoke macos-x64-jar jar",
                "Smoke Windows jar",
                "Smoke macos-arm64 dmg",
                "Smoke macos-x64 dmg",
                "Smoke Windows msi",
                "Smoke linux-deb in ubuntu:24.04",
                "Smoke linux-rpm in fedora:latest",
                "Smoke linux-appimage in ubuntu:24.04",
            )
        val jobs =
            buildJsonArray {
                passedJobs.forEachIndexed { index, name ->
                    add(
                        buildJsonObject {
                            put("id", index + 1)
                            put("name", name)
                            put("conclusion", "success")
                            put("steps", buildJsonArray {})
                        },
                    )
                }
                add(
                    buildJsonObject {
                        put("id", 99)
                        put("name", "Attach verified assets to draft release")
                        put("conclusion", "failure")
                        put(
                            "steps",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("name", "Set up job")
                                        put("conclusion", "success")
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("name", "Prepare draft and attach verified assets")
                                        put("conclusion", "failure")
                                    },
                                )
                            },
                        )
                    },
                )
            }
        Files.writeString(root.resolve("jobs.json"), buildJsonArray { add(buildJsonObject { put("jobs", jobs) }) }.toString())
        Files.writeString(
            root.resolve("failure.log"),
            "Release must be a matching draft with the correct prerelease classification.\n",
        )
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
                "SOURCE_RUN_ID" to "123",
                "RUNNER_TEMP" to root.toString(),
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
