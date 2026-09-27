package de.heckenmann.visualagent.server

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Limits a blocking image request, including its response body, to one deadline. */
internal fun <T> fetchBeforeDeadline(
    executor: ExecutorService,
    timeoutMillis: Long,
    fetch: () -> T,
): T {
    val task = executor.submit(Callable(fetch))
    try {
        return task.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (timeout: TimeoutException) {
        task.cancel(true)
        throw timeout
    } catch (interruption: InterruptedException) {
        task.cancel(true)
        Thread.currentThread().interrupt()
        throw interruption
    }
}
