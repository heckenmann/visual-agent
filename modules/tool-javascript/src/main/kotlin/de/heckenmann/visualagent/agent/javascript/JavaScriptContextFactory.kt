package de.heckenmann.visualagent.agent.javascript

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.SandboxPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

/** Creates Graal contexts with the strictest sandbox supported by the runtime. */
internal class JavaScriptContextFactory {
    private val untrustedSandboxSupported = AtomicReference<Boolean?>(null)

    /** Create a context and expose only the request-scoped bridge objects. */
    fun create(
        bridge: JavaScriptToolBridge,
        limits: JavaScriptExecutionLimits,
    ): Context {
        if (untrustedSandboxSupported.get() != false) {
            runCatching { newUntrustedContext(bridge, limits) }
                .onSuccess {
                    untrustedSandboxSupported.set(true)
                    return it
                }.onFailure {
                    // Community and stock OpenJDK runtimes do not ship the native
                    // isolate. Keep the constrained host-access boundary there;
                    // Oracle GraalVM uses this branch with hard guest-memory limits.
                    untrustedSandboxSupported.set(false)
                }
        }
        return newConstrainedContext(bridge)
    }

    private fun newUntrustedContext(
        bridge: JavaScriptToolBridge,
        limits: JavaScriptExecutionLimits,
    ): Context =
        Context
            .newBuilder("js")
            .sandbox(SandboxPolicy.UNTRUSTED)
            .`in`(ByteArrayInputStream(ByteArray(0)))
            .`out`(ByteArrayOutputStream())
            .err(ByteArrayOutputStream())
            .allowHostAccess(HostAccess.UNTRUSTED)
            .allowPolyglotAccess(PolyglotAccess.NONE)
            .option("engine.MaxIsolateMemory", "${limits.maxIsolateMemoryBytes}B")
            .option("sandbox.MaxHeapMemory", "${limits.maxGuestHeapBytes}B")
            .option("sandbox.MaxCPUTime", "${limits.timeoutMillis}ms")
            .option("sandbox.MaxStatements", MAX_STATEMENTS.toString())
            .option("sandbox.MaxStackFrames", MAX_STACK_FRAMES.toString())
            .option("sandbox.MaxThreads", "1")
            .option("sandbox.MaxASTDepth", MAX_AST_DEPTH.toString())
            .option("sandbox.MaxOutputStreamSize", "10KB")
            .option("sandbox.MaxErrorStreamSize", "10KB")
            .build()
            .also { context -> installBindings(context, bridge) }

    private fun newConstrainedContext(bridge: JavaScriptToolBridge): Context =
        Context
            .newBuilder("js")
            .sandbox(SandboxPolicy.CONSTRAINED)
            .`in`(ByteArrayInputStream(ByteArray(0)))
            .`out`(ByteArrayOutputStream())
            .err(ByteArrayOutputStream())
            .allowHostAccess(HostAccess.CONSTRAINED)
            .allowHostClassLookup { false }
            .allowHostClassLoading(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowPolyglotAccess(PolyglotAccess.NONE)
            .build()
            .also { context -> installBindings(context, bridge) }

    private fun installBindings(
        context: Context,
        bridge: JavaScriptToolBridge,
    ) {
        context.getBindings("js").putMember("tools", bridge.toolsObject())
        context.getBindings("js").putMember("workspace", bridge.workspaceObject())
        context.getBindings("js").putMember("console", bridge.consoleObject())
    }

    private companion object {
        // CPU time is the request-scoped execution bound. Keep the mandatory
        // statement limit effectively unbounded so faster GraalJS releases do
        // not turn normal timeout failures into premature statement-limit errors.
        const val MAX_STATEMENTS = Long.MAX_VALUE
        const val MAX_STACK_FRAMES = 128
        const val MAX_AST_DEPTH = 128
    }
}
