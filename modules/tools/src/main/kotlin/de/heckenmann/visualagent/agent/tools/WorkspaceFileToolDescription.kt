package de.heckenmann.visualagent.agent.tools

/** Returns the tool description for workspace:file with all actions and their parameters. */
internal fun workspaceFileToolDescription(): String =
    "Manage imported workspace files through the Visual Agent server. This tool remains available even when " +
        "the model runtime's native filesystem sandbox is read-only. For managed workspace files, do not run " +
        "native or terminal permission preflight checks such as `test -w`, and do not abort because they report " +
        "read-only. Use the relevant action below; its result is authoritative. Actions:\n" +
        "- list: {\"action\":\"list\"}. Lists all managed files and directories, including empty directories.\n" +
        "- createDirectory: {\"action\":\"createDirectory\",\"parentDirectory\":\"projects\",\"name\":\"demo\"}. " +
        "Creates an empty managed workspace directory.\n" +
        "- info: {\"action\":\"info\",\"id\":\"...\"} or {\"action\":\"info\",\"path\":\"...\"}. File metadata.\n" +
        "- delete: {\"action\":\"delete\",\"id\":\"...\"} or {\"action\":\"delete\",\"path\":\"...\"}. " +
        "Deletes one managed file and its metadata through the Visual Agent server.\n" +
        "- deleteDirectory: {\"action\":\"deleteDirectory\",\"path\":\"projects/demo\",\"recursive\":true}. " +
        "Deletes an empty directory by default; recursive deletion must be explicitly requested. " +
        "The workspace root cannot be deleted.\n" +
        "- hash: {\"action\":\"hash\",\"id\":\"...\"}. SHA-256 hash.\n" +
        "- readText: {\"action\":\"readText\",\"id\":\"...\"}. Read text content.\n" +
        "- extractPdfText: {\"action\":\"extractPdfText\",\"id\":\"...\"}. Extract text from PDF.\n" +
        "- renderPdfPage: {\"action\":\"renderPdfPage\",\"id\":\"...\",\"page\":1}. Render PDF page as image.\n" +
        "- imageInfo: {\"action\":\"imageInfo\",\"id\":\"...\"}. Image dimensions and type.\n" +
        "- imageBytes: {\"action\":\"imageBytes\",\"id\":\"...\"}. Base64-encoded image bytes.\n" +
        "- analyzeImage: {\"action\":\"analyzeImage\",\"id\":\"...\",\"prompt\":\"describe this\"}. " +
        "Analyze image with vision model.\n" +
        "For an image in the conversation, use the returned relative path as " +
        "![alt text](workspace:<path>). Do not invent paths or paste imageBytes base64 into a response.\n" +
        "- search: {\"action\":\"search\",\"query\":\"...\",\"entryType\":\"file|directory\",\"mimeType\":\"text/plain\"}. " +
        "Search files and directories; entryType and mimeType are optional.\n" +
        "- sync: {\"action\":\"sync\"}. Sync metadata with filesystem. " +
        "Use id or path to identify files."
