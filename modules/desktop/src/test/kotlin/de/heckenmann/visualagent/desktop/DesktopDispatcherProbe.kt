package de.heckenmann.visualagent.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

/** Executes with production dependencies only; no test framework initializes the main dispatcher. */
object DesktopDispatcherProbe {
    /** Fails the isolated process if dispatching to the real UI thread is unavailable. */
    @JvmStatic
    fun main(args: Array<String>) {
        val result =
            runCatching {
                runBlocking {
                    withContext(Dispatchers.Main) {
                        check(!Dispatchers.Main.immediate.isDispatchNeeded(coroutineContext))
                    }
                }
            }
        result.exceptionOrNull()?.printStackTrace()
        exitProcess(if (result.isSuccess) 0 else 1)
    }
}
