import org.gradle.api.Action
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.initialization.Settings

gradle.settingsEvaluated(Action<Settings> {
    val coordinates =
        buildscript.configurations.getByName("classpath").incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                (component.id as? ModuleComponentIdentifier)?.let { id ->
                    "${id.group}:${id.module}:${id.version}"
                }
            }.distinct()
            .sorted()
    val report = rootDir.resolve("build/reports/dependency-license/settings-plugins.csv")
    report.parentFile.mkdirs()
    report.writeText((listOf("coordinate") + coordinates).joinToString("\n", postfix = "\n"))
})
