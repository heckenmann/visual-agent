package de.heckenmann.visualagent.release

import de.heckenmann.visualagent.release.bundles.ReleaseMaterialBundles
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Runs release verification without introducing tooling into the application runtime. */
fun main(args: Array<String>) {
    try {
        if (args.firstOrNull() == "verify-appimage-runtime") {
            require(args.size == 3) { "Usage: release-tools verify-appimage-runtime SOURCE_RUNTIME APPIMAGE" }
            val source = Path.of(args[1])
            val packaged = Path.of(args[2])
            val output = Files.createDirectories(Path.of("build"))

            /** Reads the generated digest section without loading or executing the package. */
            fun section(file: Path): Pair<Int, Int> =
                AppImageRuntime.parseDigestSection(
                    runEvidenceCommand(listOf("readelf", "--wide", "--sections", file.toString()), output),
                )
            AppImageRuntime.verifyPrefix(source, packaged, section(source), section(packaged))
            println("AppImage embeds the source-built runtime; only appimagetool's generated MD5 section differs.")
            return
        }
        if (args.firstOrNull() == "build-bundles") {
            ReleaseMaterialBundles.build(bundleRequest(args.drop(1)))
            return
        }
        if (args.firstOrNull() == "collect-notices") {
            NoticeCollector.collect(noticeRequest(args.drop(1)))
            return
        }
        if (args.firstOrNull() == "verify-supplements") {
            require(args.size == 3 && args[1] == "--registry") {
                "Usage: release-tools verify-supplements --registry FILE"
            }
            val registry = Path.of(args[2])
            val supplements = SupplementalEvidence.load(registry)
            val runtimes = RuntimePackageEvidence.load(registry, supplements)
            println("Verified ${supplements.size} supplemental artifacts and ${runtimes.size} runtime mappings; review remains required.")
            return
        }
        require(args.firstOrNull() == "verify-assets") {
            "Usage: release-tools verify-assets --assets DIRECTORY [--uploaded]"
        }
        val options = args.drop(1)
        val paths = namedPathOptions(options.filter { it != "--uploaded" }, setOf("assets"))
        val directory = requireNotNull(paths["assets"]) { "Missing required option: --assets" }
        if ("--uploaded" in options) {
            ReleaseAssets.verifyUploaded(directory, Json.parseToJsonElement(System.`in`.bufferedReader().readText()))
            println("All twelve uploaded assets match the build.")
        } else {
            ReleaseAssets.verify(directory)
            println("All ten packages and both recipient legal/source archives are present and readable.")
        }
    } catch (error: Exception) {
        System.err.println("Release asset check: ${error.message}")
        exitProcess(1)
    }
}
