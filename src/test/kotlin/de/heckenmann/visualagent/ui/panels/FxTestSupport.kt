package de.heckenmann.visualagent.ui.panels

import javafx.application.Platform
import javafx.fxml.FXMLLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object FxTestSupport {
    fun startToolkit() {
        FXMLLoader.setDefaultClassLoader(FxTestSupport::class.java.classLoader)
        val started = CountDownLatch(1)
        runCatching { Platform.startup(started::countDown) }
            .onFailure { error ->
                if (error !is IllegalStateException || error.message != "Toolkit already initialized") throw error
                started.countDown()
            }
        check(started.await(5, TimeUnit.SECONDS)) { "JavaFX toolkit did not start" }
        Platform.setImplicitExit(false)
    }

    fun <T> run(action: () -> T): T {
        if (Platform.isFxApplicationThread()) return action()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<T>>()
        Platform.runLater {
            result.set(runCatching(action))
            completed.countDown()
        }
        check(completed.await(10, TimeUnit.SECONDS)) { "JavaFX action timed out" }
        return result.get().getOrThrow()
    }

    fun flush() {
        run { Unit }
    }
}
