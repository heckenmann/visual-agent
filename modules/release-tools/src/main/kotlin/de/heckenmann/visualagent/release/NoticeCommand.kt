package de.heckenmann.visualagent.release

import java.nio.file.Path

/** Parses the existing notice collector's named path options without a separate scripting runtime. */
internal fun noticeRequest(arguments: List<String>): NoticeCollectionRequest {
    val allowed =
        setOf(
            "package-root",
            "inventory",
            "output",
            "license-report",
            "project-license",
            "supplements",
            "appimage-package",
            "appimage-runtime",
            "appimage-source-archive",
        )
    val paths = namedPathOptions(arguments, allowed)

    /** Rejects omitted mandatory paths before collecting any evidence. */
    fun required(name: String): Path = requireNotNull(paths[name]) { "Missing required notice option: --$name" }
    val appKeys = listOf("appimage-package", "appimage-runtime", "appimage-source-archive")
    val appImage =
        if (appKeys.any { it in paths }) {
            require(appKeys.all { it in paths }) { "AppImage evidence requires the package, runtime, and source archive" }
            AppImageNoticeInputs(required(appKeys[0]), required(appKeys[1]), required(appKeys[2]))
        } else {
            null
        }
    return NoticeCollectionRequest(
        required("package-root"),
        required("inventory"),
        required("output"),
        required("license-report"),
        required("project-license"),
        paths["supplements"],
        appImage,
    )
}
