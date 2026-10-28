rootProject.name = "visual-agent"

apply(from = "gradle/release-version.settings.gradle.kts")

include(
    ":application",
    ":ui",
    ":protocol",
    ":desktop",
    ":agent-core",
    ":model-core",
    ":task-core",
    ":provider-core",
    ":provider-standard",
    ":provider-openai-codex",
    ":provider-codex-direct",
    ":providers",
    ":tool-standard",
    ":tool-javascript",
    ":tools",
)

project(":ui").projectDir = file("modules/ui")
project(":protocol").projectDir = file("modules/protocol")
project(":desktop").projectDir = file("modules/desktop")
project(":task-core").projectDir = file("modules/task-core")
project(":agent-core").projectDir = file("modules/agent-core")
project(":model-core").projectDir = file("modules/model-core")
project(":provider-core").projectDir = file("modules/provider-core")
project(":provider-standard").projectDir = file("modules/providers")
project(":provider-openai-codex").projectDir = file("modules/provider-openai-codex")
project(":provider-codex-direct").projectDir = file("modules/provider-codex-direct")
project(":providers").projectDir = file("modules/providers-bundle")
project(":tool-standard").projectDir = file("modules/tools")
project(":tool-javascript").projectDir = file("modules/tool-javascript")
project(":tools").projectDir = file("modules/tools-bundle")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
