package de.heckenmann.visualagent.agent.javascript

import java.util.concurrent.atomic.AtomicLong

/** Tracks bounded workspace traffic shared by direct JavaScript helpers. */
internal class JavaScriptWorkspaceBudget(
    private val limits: JavaScriptExecutionLimits,
    private val failure: (JavaScriptErrorCategory, String) -> JavaScriptExecutionException,
) {
    private val workspaceReadBytes = AtomicLong()
    private val workspaceBytes = AtomicLong()

    fun releaseWrite(bytes: Long) {
        workspaceBytes.addAndGet(-bytes)
    }

    fun reserveWrite(bytes: Long) {
        if (bytes > limits.maxWorkspaceWriteBytes) {
            throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace write size limit exceeded")
        }
        while (true) {
            val current = workspaceBytes.get()
            val next = current + bytes
            if (next < current || next > limits.maxWorkspaceBytes) {
                throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace write budget exceeded")
            }
            if (workspaceBytes.compareAndSet(current, next)) return
        }
    }

    fun availableRead(): Long =
        minOf(
            limits.maxWorkspaceReadBytes,
            (limits.maxWorkspaceReadTotalBytes - workspaceReadBytes.get()).coerceAtLeast(0),
        )

    fun reserveRead(bytes: Long) {
        if (bytes < 0 || bytes > limits.maxWorkspaceReadBytes) {
            throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace read size limit exceeded")
        }
        while (true) {
            val current = workspaceReadBytes.get()
            val next = current + bytes
            if (next < current || next > limits.maxWorkspaceReadTotalBytes) {
                throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace read budget exceeded")
            }
            if (workspaceReadBytes.compareAndSet(current, next)) return
        }
    }
}
