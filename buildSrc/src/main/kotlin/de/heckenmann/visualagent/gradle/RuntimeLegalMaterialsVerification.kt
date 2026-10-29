package de.heckenmann.visualagent.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

/** Verify retained source provenance and report packaged hashes without modifying signed bundles. */
fun verifyPackagedRuntimeLegalMaterials(
    resources: File,
    runtimeHome: File,
    sourceJdk: File,
    ownLicense: File,
): Map<String, String> {
    val legal = resources.resolve("legal/java-runtime")
    val retained = loadRuntimeProperties(legal.resolve("source-jdk.properties"))
    val source = sourceJdk.toPath().toRealPath()
    val original = loadRuntimeProperties(source.resolve("release").toFile())
    val publicKeys = publicRuntimeMetadataKeys().toSet()
    check(retained.stringPropertyNames() == original.stringPropertyNames().intersect(publicKeys)) {
        "Retained JDK metadata has missing or unexpected fields"
    }
    retained.stringPropertyNames().forEach { key ->
        check(retained.getProperty(key) == original.getProperty(key)) { "Retained source-JDK metadata changed: $key" }
    }
    val runtimeVersion = loadRuntimeProperties(runtimeHome.resolve("release")).getProperty("JAVA_VERSION").trim('"')
    check(retained.getProperty("JAVA_VERSION").trim('"') == runtimeVersion) { "Runtime legal provenance has a different Java version" }
    val suffix = if (sourceJdk.resolve("bin/keytool.exe").isFile) ".exe" else ""
    val records =
        legal.resolve("source-inputs.sha256").readLines().map { line ->
            val parts = line.split("  ", limit = 2)
            check(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}"))) { "Malformed runtime provenance record" }
            parts[1] to parts[0]
        }
    check(records.map { it.first }.distinct().size == records.size) { "Duplicate runtime provenance record" }
    val inputs = records.toMap()
    val required =
        listOf(
            "release",
            "lib/modules",
            "bin/keytool$suffix",
            "bin/jlink$suffix",
            "bin/jpackage$suffix",
            "legal/java.base/LICENSE",
            "legal/java.base/ADDITIONAL_LICENSE_INFO",
            "legal/java.base/ASSEMBLY_EXCEPTION",
        )
    val sourceInputs = runtimeSourceInputs(source)
    check(inputs.keys.containsAll(required) && inputs.keys == sourceInputs.keys) { "Incomplete or divergent runtime provenance" }
    inputs.forEach { (relative, expected) ->
        val originalFile = sourceInputs.getValue(relative)
        check(runtimeLegalSha256(originalFile) == expected) { "Source JDK changed after preparing provenance: $relative" }
        val copy =
            when {
                relative.startsWith("legal/") -> legal.resolve(relative)
                '/' !in relative && relative != "release" -> legal.resolve("vendor/$relative")
                else -> null
            }
        if (copy != null) {
            check(copy.isFile && runtimeLegalSha256(copy) == expected) { "Retained JDK notice changed: $relative" }
        }
    }
    check(resources.resolve("legal/visual-agent/LICENSE").readBytes().contentEquals(ownLicense.readBytes())) {
        "Native package is missing the separate first-party license"
    }
    listOf("lib/modules", "bin/keytool$suffix").forEach { relative ->
        val file = runtimeHome.resolve(relative)
        check(file.isFile && file.length() > 0) { "Packaged runtime input is missing or empty: $relative" }
    }
    // jlink transforms modules and macOS jpackage re-signs executables. Keep both
    // identities; byte equality across these supported packaging stages is invalid.
    return sortedMapOf(
        "source.java-runtime-version" to retained.getProperty("JAVA_RUNTIME_VERSION").trim('"'),
        "source.vendor" to retained.getProperty("IMPLEMENTOR").trim('"'),
        "source.release.sha256" to inputs.getValue("release"),
        "source.modules.sha256" to inputs.getValue("lib/modules"),
        "source.keytool.sha256" to inputs.getValue("bin/keytool$suffix"),
        "packaged.modules.sha256" to runtimeLegalSha256(runtimeHome.resolve("lib/modules")),
        "packaged.keytool.sha256" to runtimeLegalSha256(runtimeHome.resolve("bin/keytool$suffix")),
    )
}

/** Public vendor/version fields only; never serialize arbitrary local JDK properties. */
internal fun publicRuntimeMetadataKeys(): List<String> =
    listOf(
        "JAVA_VERSION",
        "JAVA_RUNTIME_VERSION",
        "JAVA_VERSION_DATE",
        "IMPLEMENTOR",
        "IMPLEMENTOR_VERSION",
        "OS_NAME",
        "OS_ARCH",
    )

/** Compute a bounded-memory digest for source and packaged runtime inputs. */
internal fun runtimeLegalSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun loadRuntimeProperties(file: File): Properties = Properties().apply { file.inputStream().use(::load) }

/** Enumerate exact toolchain identities and retained legal files without following external links. */
internal fun runtimeSourceInputs(root: Path): Map<String, File> {
    val inputs = sortedMapOf<String, File>()

    fun add(path: Path) {
        val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
        check(Files.isRegularFile(path) && path.toRealPath().startsWith(root)) { "Missing or escaping source-JDK file: $relative" }
        check(path.toFile().length() > 0) { "Empty source-JDK legal/input file: $relative" }
        inputs[relative] = path.toFile()
    }
    val suffix = if (Files.isRegularFile(root.resolve("bin/keytool.exe"))) ".exe" else ""
    listOf("release", "lib/modules", "bin/keytool$suffix", "bin/jlink$suffix", "bin/jpackage$suffix").forEach { add(root.resolve(it)) }
    val legal = root.resolve("legal")
    listOf("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION").forEach { add(legal.resolve("java.base/$it")) }
    Files.walk(legal).use { paths ->
        paths.forEach { path ->
            check(!Files.isSymbolicLink(path) || !Files.isDirectory(path)) { "JDK legal directory links require explicit review" }
            if (!Files.isDirectory(path)) add(path)
        }
    }
    val notice = Regex("(?i)(LICENSE|NOTICE|COPYING|COPYRIGHT|DISCLAIMER|README|THIRD_PARTY_README)([._-].*)?")
    root
        .toFile()
        .listFiles()
        .orEmpty()
        .filter { notice.matches(it.name) }
        .forEach { add(it.toPath()) }
    return inputs
}
