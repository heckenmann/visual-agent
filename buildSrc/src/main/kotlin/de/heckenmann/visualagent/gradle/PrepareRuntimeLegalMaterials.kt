package de.heckenmann.visualagent.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties
import javax.inject.Inject

/** Retains source-JDK notices and build provenance without claiming redistribution clearance. */
@CacheableTask
abstract class PrepareRuntimeLegalMaterials
    @Inject
    constructor(
        private val fileSystem: FileSystemOperations,
    ) : DefaultTask() {
        /** Exact JDK selected for packaging, including its modules image and vendor legal files. */
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val jdkHome: DirectoryProperty

        /** First-party license, kept separate from the runtime's own license terms. */
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val projectLicense: RegularFileProperty

        /** Compose app-resources root containing a common legal directory. */
        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        /** Copy legal material verbatim and record deterministic, host-path-free provenance. */
        @TaskAction
        fun prepare() {
            val source =
                jdkHome
                    .get()
                    .asFile
                    .toPath()
                    .toRealPath()
            val sourceInputs = runtimeSourceInputs(source)
            val properties = Properties().apply { sourceInputs.getValue("release").inputStream().use(::load) }
            listOf("JAVA_VERSION", "JAVA_RUNTIME_VERSION", "IMPLEMENTOR").forEach { key ->
                check(
                    !properties
                        .getProperty(key)
                        ?.trim()
                        ?.removeSurrounding("\"")
                        .isNullOrBlank(),
                ) {
                    "Source JDK is missing public build metadata: $key"
                }
            }
            val destination = outputDirectory.get().asFile
            fileSystem.delete { delete(destination) }
            val runtime = destination.resolve("common/legal/java-runtime").apply { mkdirs() }
            val sourceHashes = sourceInputs.mapValues { (_, file) -> runtimeLegalSha256(file) }
            val publicKeys = publicRuntimeMetadataKeys()
            publicKeys.forEach { key ->
                check(
                    properties.getProperty(key).orEmpty().none(Char::isISOControl),
                ) { "Invalid control character in public JDK metadata: $key" }
            }
            runtime.resolve("source-jdk.properties").writeText(
                publicKeys
                    .sorted()
                    .mapNotNull { key -> properties.getProperty(key)?.let { value -> "$key=$value" } }
                    .joinToString("\n", postfix = "\n"),
            )
            sourceInputs.forEach { (relative, file) ->
                when {
                    relative.startsWith("legal/") -> copy(file, runtime.resolve(relative))
                    '/' !in relative && relative != "release" -> copy(file, runtime.resolve("vendor/$relative"))
                }
            }
            runtime.resolve("source-inputs.sha256").writeText(
                sourceHashes.toSortedMap().map { (name, hash) -> "$hash  $name" }.joinToString("\n", postfix = "\n"),
            )
            runtime.resolve("README.txt").writeText(
                "Retained source-JDK legal files and public build provenance.\n" +
                    "source-inputs.sha256 identifies original toolchain inputs, not files copied into this directory.\n" +
                    "This is not complete corresponding source or a redistribution clearance. See issue #410.\n",
            )
            val ownLicense = projectLicense.get().asFile
            check(ownLicense.length() > 0) { "Project license is empty" }
            copy(ownLicense, destination.resolve("common/legal/visual-agent/LICENSE"))
        }

        private fun copy(
            source: File,
            target: File,
        ) {
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }
