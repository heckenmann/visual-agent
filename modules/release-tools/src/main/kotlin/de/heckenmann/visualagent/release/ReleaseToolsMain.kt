package de.heckenmann.visualagent.release

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.system.exitProcess

/** Runs recipient verification outside the application runtime. */
fun main(args: Array<String>) {
    try {
        require(args.size in 3..4 && args[0] == "verify-assets" && args[1] == "--assets") {
            "Usage: release-tools verify-assets --assets DIRECTORY [--uploaded]"
        }
        val directory = Path.of(args[2])
        if (args.size == 4) {
            require(args[3] == "--uploaded") { "Unknown option: ${args[3]}" }
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
