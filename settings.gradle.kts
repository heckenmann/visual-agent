rootProject.name = "visual-agent"

include(":application", ":ui", ":protocol", ":desktop", ":providers", ":tools")

project(":ui").projectDir = file("modules/ui")
project(":protocol").projectDir = file("modules/protocol")
project(":desktop").projectDir = file("modules/desktop")
project(":providers").projectDir = file("modules/providers")
project(":tools").projectDir = file("modules/tools")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
