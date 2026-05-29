package de.heckenmann.visualagent.ui

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import mu.KotlinLogging

/**
 * Utility for loading FXML files with type-safe controller binding.
 *
 * Sets the controller before loading so no `fx:controller` attribute is needed in FXML.
 * All FXML files are loaded from the `/fxml/` classpath directory.
 */
object FxmlLoader {
    private val logger = KotlinLogging.logger {}

    /**
     * Loads an FXML file and binds it to the given controller instance.
     *
     * @param controller The controller instance to bind (typically `this` from the calling panel)
     * @param fxmlFile The FXML filename relative to `/fxml/` (e.g. `"application-settings.fxml"`)
     * @return The root [Parent] node of the loaded FXML
     */
    fun load(
        controller: Any,
        fxmlFile: String,
    ): Parent {
        val resource =
            controller::class.java.getResource("/fxml/$fxmlFile")
                ?: throw IllegalStateException("FXML file not found: /fxml/$fxmlFile")
        val loader = FXMLLoader(resource)
        loader.setController(controller)
        logger.debug { "Loading FXML: /fxml/$fxmlFile with controller: ${controller::class.simpleName}" }
        return loader.load()
    }
}
