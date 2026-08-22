rootProject.name = "visual-agent"

include(
    ":application",
    ":ui",
    ":protocol",
    ":desktop",
    ":agent-core",
    ":provider-core",
    ":provider-standard",
    ":provider-openai-codex",
    ":providers",
    ":tool-standard",
    ":tool-javascript",
    ":tools",
)

project(":ui").projectDir = file("modules/ui")
project(":protocol").projectDir = file("modules/protocol")
project(":desktop").projectDir = file("modules/desktop")
project(":agent-core").projectDir = file("modules/agent-core")
project(":provider-core").projectDir = file("modules/provider-core")
project(":provider-standard").projectDir = file("modules/providers")
project(":provider-openai-codex").projectDir = file("modules/provider-openai-codex")
project(":providers").projectDir = file("modules/providers-bundle")
project(":tool-standard").projectDir = file("modules/tools")
project(":tool-javascript").projectDir = file("modules/tool-javascript")
project(":tools").projectDir = file("modules/tools-bundle")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
