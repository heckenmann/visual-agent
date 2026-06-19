package de.heckenmann.visualagent.ui.panels.canvas

import org.jhotdraw8.application.ApplicationLabels
import org.jhotdraw8.application.resources.ClasspathResources
import org.jhotdraw8.draw.DrawLabels
import org.jhotdraw8.draw.tool.SelectionTool
import java.util.Locale

internal const val PEN_COLOR = "#2457d6"
internal const val PEN_WIDTH = 2.0
internal const val MAX_INSERTED_IMAGE_SIZE = 480.0

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
internal annotation class GeneratedUiGlue

internal fun createSelectionTool(): SelectionTool {
    val locale = Locale.getDefault()
    val applicationLabels = ClasspathResources("org.jhotdraw8.application.Labels", locale)
    val drawLabels = ClasspathResources("org.jhotdraw8.draw.Labels", locale)
    ApplicationLabels.setResources(applicationLabels)
    ApplicationLabels.setGuiResources(applicationLabels)
    configureDrawLabels(drawLabels)
    return SelectionTool(
        "tool.selectFigure",
        drawLabels,
    )
}

private fun configureDrawLabels(resources: ClasspathResources) {
    // JHotDraw exposes no setter and otherwise assumes that it runs as a named Java module.
    DrawLabels::class.java.getDeclaredField("labels").apply {
        isAccessible = true
        set(null, resources)
    }
}
