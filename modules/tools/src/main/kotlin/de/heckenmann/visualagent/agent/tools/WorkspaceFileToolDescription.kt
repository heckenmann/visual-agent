package de.heckenmann.visualagent.agent.tools

/** Returns the tool description for workspace:file with all actions and their parameters. */
internal fun workspaceFileToolDescription(): String =
    "Manage workspace files through the Visual Agent server. This tool remains available even when " +
        "the model runtime's native filesystem sandbox is read-only. For managed workspace files, do not run " +
        "native or terminal permission preflight checks such as `test -w`, and do not abort because they report " +
        "read-only. Use the relevant action below; its result is authoritative. Actions:\n" +
        "- listRoots: {\"action\":\"listRoots\"}. Lists the managed workspace and every explicitly granted directory. " +
        "Each root has an opaque id; do not infer native paths.\n" +
        "- list: {\"action\":\"list\",\"rootId\":\"workspace|...\",\"path\":\"\"}. Lists entries in a root. " +
        "Use a grant id returned by listRoots for client- or server-origin directories.\n" +
        "- createDirectory: {\"action\":\"createDirectory\",\"parentDirectory\":\"projects\", " +
        "\"name\":\"demo\"} for the managed workspace, " +
        "or {\"action\":\"createDirectory\",\"rootId\":\"...\",\"path\":\"projects/demo\"} for a read-write granted root.\n" +
        "- info: {\"action\":\"info\",\"id\":\"...\"} or {\"action\":\"info\",\"path\":\"...\"}. File metadata.\n" +
        "- delete: {\"action\":\"delete\",\"id\":\"...\"} for a managed file, or " +
        "{\"action\":\"delete\",\"rootId\":\"...\",\"path\":\"old.txt\",\"recursive\":false} for a listed root. " +
        "Deletes one file or directory through its owner-side capability.\n" +
        "- deleteDirectory: {\"action\":\"deleteDirectory\",\"path\":\"projects/demo\",\"recursive\":true}. " +
        "Deletes an empty directory by default; recursive deletion must be explicitly requested. " +
        "The workspace root cannot be deleted.\n" +
        "- hash: {\"action\":\"hash\",\"id\":\"...\"}. SHA-256 hash.\n" +
        "- readText: {\"action\":\"readText\",\"id\":\"...\"} for a managed file, or " +
        "{\"action\":\"readText\",\"rootId\":\"...\",\"path\":\"notes.md\"} for any listed root.\n" +
        "- mime: {\"action\":\"mime\",\"id\":\"...\"} for a managed file, or " +
        "{\"action\":\"mime\",\"rootId\":\"...\",\"path\":\"notes.md\"} for a granted root. " +
        "Detects content type from bounded bytes; do not trust file extensions.\n" +
        "- writeText: {\"action\":\"writeText\",\"rootId\":\"workspace|...\",\"path\":\"notes.md\", " +
        "\"content\":\"...\"}. " +
        "Writes text through the selected root's capability; a read-only grant is rejected.\n" +
        "- edit: {\"action\":\"edit\",\"rootId\":\"workspace|...\",\"path\":\"notes.md\", " +
        "\"oldText\":\"exact old text\",\"newText\":\"replacement\"}. oldText must occur exactly once.\n" +
        "- copy: {\"action\":\"copy\",\"sourceRootId\":\"...\",\"sourcePath\":\"report.md\", " +
        "\"targetRootId\":\"...\",\"targetPath\":\"archive/report.md\"}. Copies one regular file. " +
        "The source may be read-only; the target must be read-write. Server-owned grants may use different root ids.\n" +
        "- move: {\"action\":\"move\",\"sourceRootId\":\"...\",\"sourcePath\":\"draft.md\", " +
        "\"targetRootId\":\"...\",\"targetPath\":\"published/draft.md\"}. Copies and verifies the target before deleting " +
        "the source. Both roots must be read-write. Client-owned and workspace-to-grant transfers require the file-exchange transport.\n" +
        "- glob: {\"action\":\"glob\",\"rootId\":\"workspace|...\",\"path\":\"optional/subdirectory\", " +
        "\"pattern\":\"**/*.md\"}. Finds up to 500 regular files; the pattern is relative to path.\n" +
        "- grep: {\"action\":\"grep\",\"rootId\":\"workspace|...\",\"path\":\"optional/subdirectory\", " +
        "\"query\":\"text\"}. Searches up to 1,000 bounded text files and returns matching lines.\n" +
        "- extractPdfText: {\"action\":\"extractPdfText\",\"id\":\"...\"}. Extract text from PDF.\n" +
        "- renderPdfPage: {\"action\":\"renderPdfPage\",\"id\":\"...\",\"page\":1}. Render PDF page as image.\n" +
        "- imageInfo: {\"action\":\"imageInfo\",\"id\":\"...\"}. Image dimensions and type.\n" +
        "- imageBytes: {\"action\":\"imageBytes\",\"id\":\"...\"}. Base64-encoded image bytes.\n" +
        "- analyzeImage: {\"action\":\"analyzeImage\",\"id\":\"...\",\"prompt\":\"describe this\"}. " +
        "Analyze image with vision model.\n" +
        "For an image in the conversation, use `![alt text](workspace:<path>)` for the managed workspace or " +
        "`![alt text](visual-agent-file://<rootId>/<path>)` for a granted root. " +
        "Do not invent paths or paste imageBytes base64 into a response.\n" +
        "- search: {\"action\":\"search\",\"rootId\":\"workspace|...\",\"path\":\"optional/subdirectory\",\"query\":\"...\", " +
        "\"entryType\":\"file|directory\",\"mimeType\":\"text/plain\"}. " +
        "Search files and directories; entryType and mimeType are optional.\n" +
        "- sync: {\"action\":\"sync\"}. Sync metadata with filesystem. " +
        "Use id or path to identify files."
