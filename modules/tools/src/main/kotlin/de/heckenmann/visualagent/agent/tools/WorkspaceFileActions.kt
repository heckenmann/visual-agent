package de.heckenmann.visualagent.agent.tools

/** Explicit registered actions for capability routing and contract validation. */
internal val workspaceFileActions =
    setOf(
        "listRoots",
        "list",
        "createDirectory",
        "search",
        "glob",
        "grep",
        "writeText",
        "edit",
        "copy",
        "move",
        "info",
        "sync",
        "delete",
        "deleteDirectory",
        "hash",
        "readText",
        "mime",
        "extractPdfText",
        "renderPdfPage",
        "imageInfo",
        "imageBytes",
        "analyzeImage",
        "analyzeFile",
    )
