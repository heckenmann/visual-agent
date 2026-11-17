package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.noticeSha256
import java.nio.file.Files
import java.nio.file.Path

/** Complete six-platform review/recipient archive inputs matching the legacy bundler fixtures. */
internal class BundleFixture(
    parent: Path,
) {
    val root: Path = Files.createTempDirectory(parent, "bundle-")
    val project: Path = root.resolve("project")
    val sources: Path = root.resolve("sources")
    val notices: Path = root.resolve("notices")
    val output: Path = root.resolve("output")
    val request: BundleRequest get() = BundleRequest(project, sources, notices, output)

    init {
        write(project.resolve("LICENSE"), "Project license")
        write(project.resolve("docs/licensing/README.md"), "Review status")
        write(project.resolve("docs/licensing/source-delivery.md"), "Scope remains partial")
        val legal = project.resolve("docs/licensing/third-party")
        val vendor = write(legal.resolve("vendor/LICENSE"), "Vendor license")
        val historical = write(legal.resolve("historical/LICENSE"), "Historical license")
        val apache = write(legal.resolve("Apache-2.0.txt"), "Apache license")
        write(sources.resolve("archives/vendor/source.tar.gz"), "source archive bytes")
        val runtime = write(sources.resolve("archives/appimage-runtime/runtime.tar.gz"), "pinned runtime source")
        write(sources.resolve("legal/vendor/LICENSE"), "Vendor license copy")
        write(sources.resolve("source-delivery.md"), "Not complete or approved")
        val runtimeHash = noticeSha256(runtime)
        val commit = "a".repeat(40)
        write(
            legal.resolve("sources.json"),
            """{
          "redistributionApproved":false,
          "components":[{"reviewStatus":"unreviewed","materialPaths":["vendor/LICENSE"],
          "sourceArtifacts":{"example:runtime:1":"$runtimeHash","example:static:1":"${"c".repeat(64)}"},"upstreamCommit":"$commit"},
          {"reviewStatus":"unreviewed","materialPaths":["historical/LICENSE"],
          "sourceArtifacts":{"example:historical:1":"${"b".repeat(64)}"},"upstreamCommit":"$commit"}],
          "runtimePackages":[{"package":"linux-appimage","runtimeSourceArtifact":"example:runtime:1",
          "sourceArchivePath":"archives/appimage-runtime/runtime.tar.gz",
          "sourceArchives":[{"path":"archives/appimage-runtime/runtime.tar.gz","sha256":"$runtimeHash"}],
          "linkedComponents":["example:static:1"]}],
          "embeddedAssets":[],"materials":[{"path":"vendor/LICENSE","sha256":"${noticeSha256(vendor)}"},
          {"path":"historical/LICENSE","sha256":"${noticeSha256(historical)}"},
          {"path":"Apache-2.0.txt","sha256":"${noticeSha256(apache)}"}]}""",
        )
        bundleNoticePlatforms.forEach { platform ->
            val directory = notices.resolve(platform)
            val notice = write(directory.resolve("artifacts/package-hash/LICENSE"), "$platform dependency notice")
            val runtimeNotice = write(directory.resolve("runtime-legal/vendor/readme.txt"), "Runtime legal file")
            val appImage =
                if (platform == "linux-appimage") {
                    val text =
                        write(directory.resolve("appimage-runtime-legal/example-static-1/LICENSE"), "AppImage runtime component license")
                    """, "appImageRuntime":{"reviewStatus":"unreviewed","packageSha256":"${"d".repeat(64)}",
                 "runtimeSha256":"${"e".repeat(64)}","sourceArchiveSha256":"$runtimeHash","sourceCommit":"$commit",
                 "sourceArchives":[{"path":"archives/appimage-runtime/runtime.tar.gz","sha256":"$runtimeHash"}],
                 "embeddedPrefixVerified":true,"components":[{"coordinate":"example:runtime:1","sourceArtifactSha256":"$runtimeHash",
                 "upstreamCommit":"$commit","reviewStatus":"unreviewed","materials":[]},
                 {"coordinate":"example:static:1","sourceArtifactSha256":"${"c".repeat(64)}","upstreamCommit":"$commit",
                 "reviewStatus":"unreviewed","materials":[{"path":"appimage-runtime-legal/example-static-1/LICENSE",
                 "sha256":"${noticeSha256(
                        text,
                    )}"}]}]}"""
                } else {
                    ""
                }
            write(
                directory.resolve("manifest.json"),
                """{"redistributionApproved":false,"projectLicenseSha256":"${noticeSha256(project.resolve("LICENSE"))}",
              "components":[{"coordinate":"example:component:1","reviewStatus":"unreviewed",
              "licenseMetadata":{"declaredLicenses":[{"name":"Apache-2.0"}]},"noticeStatus":"retained",
              "notices":[{"path":"artifacts/package-hash/LICENSE","sha256":"${noticeSha256(notice)}"}]}],
              "runtimeLegal":{"packagePath":"Visual Agent/resources/legal/java-runtime","reviewStatus":"unreviewed",
              "files":[{"packagePath":"Visual Agent/resources/legal/java-runtime/vendor/readme.txt",
              "path":"runtime-legal/vendor/readme.txt",
              "sha256":"${noticeSha256(runtimeNotice)}","size":${Files.size(runtimeNotice)}}]}$appImage}""",
            )
        }
    }

    fun write(
        path: Path,
        text: String,
    ): Path {
        Files.createDirectories(path.parent)
        return Files.writeString(path, text)
    }
}
