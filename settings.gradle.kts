rootProject.name = "visual-agent"

includeBuild("third_party/cokit")
include(":application", ":ui", ":providers")

project(":ui").projectDir = file("modules/ui")
project(":providers").projectDir = file("modules/providers")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
