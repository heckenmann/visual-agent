rootProject.name = "visual-agent"

includeBuild("third_party/cokit")
include(":application", ":ui", ":providers", ":tools")

project(":ui").projectDir = file("modules/ui")
project(":providers").projectDir = file("modules/providers")
project(":tools").projectDir = file("modules/tools")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
