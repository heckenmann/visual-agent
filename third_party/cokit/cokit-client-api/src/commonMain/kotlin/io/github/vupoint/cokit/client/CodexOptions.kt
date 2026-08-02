package io.github.vupoint.cokit.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CodexHostPath(val value: String)

@Serializable
@JvmInline
value class ModelName(val value: String)

@Serializable
@JvmInline
value class ApprovalPolicy(val value: String) {
    companion object {
        val Untrusted = ApprovalPolicy("untrusted")
        val OnFailure = ApprovalPolicy("on-failure")
        val OnRequest = ApprovalPolicy("on-request")
        val Never = ApprovalPolicy("never")
    }
}

@Serializable
@JvmInline
value class SandboxMode(val value: String) {
    companion object {
        val ReadOnly = SandboxMode("read-only")
        val WorkspaceWrite = SandboxMode("workspace-write")
        val DangerFullAccess = SandboxMode("danger-full-access")
    }
}

@Serializable
sealed interface SandboxPolicy {
    @Serializable
    @SerialName("readOnly")
    data object ReadOnly : SandboxPolicy

    @Serializable
    @SerialName("workspaceWrite")
    data object WorkspaceWrite : SandboxPolicy

    @Serializable
    @SerialName("dangerFullAccess")
    data object DangerFullAccess : SandboxPolicy
}

@Serializable
@JvmInline
value class ReasoningEffort(val value: String) {
    companion object {
        val Low = ReasoningEffort("low")
        val Medium = ReasoningEffort("medium")
        val High = ReasoningEffort("high")
    }
}
