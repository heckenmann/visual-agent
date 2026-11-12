package de.heckenmann.visualagent.agent.javascript

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.SandboxPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Creates Graal contexts with the host-restricted sandbox supported by the packaged runtime. */
internal class JavaScriptContextFactory {
    /** Create a context and expose only the request-scoped bridge objects. */
    fun create(bridge: JavaScriptToolBridge): Context = newConstrainedContext(bridge)

    private fun newConstrainedContext(bridge: JavaScriptToolBridge): Context =
        Context
            .newBuilder("js")
            .sandbox(SandboxPolicy.CONSTRAINED)
            .`in`(ByteArrayInputStream(ByteArray(0)))
            .`out`(ByteArrayOutputStream())
            .err(ByteArrayOutputStream())
            .allowHostAccess(HostAccess.CONSTRAINED)
            // A rejecting predicate still exposes Java builtins; null disables lookup entirely.
            // See Context.Builder.allowHostClassLookup in the GraalVM SDK API contract.
            .allowHostClassLookup(null)
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
        bridge.installPromiseFactory(context)
        context.getBindings("js").putMember("tools", bridge.toolsObject())
        context.getBindings("js").putMember("workspace", bridge.workspaceObject())
        context.getBindings("js").putMember("console", bridge.consoleObject())
    }
}
