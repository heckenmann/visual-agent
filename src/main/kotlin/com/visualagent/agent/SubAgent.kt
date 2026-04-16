package com.visualagent.agent

enum class AgentStatus {
    IDLE,
    BUSY,
    OFFLINE,
}

data class SubAgent(
    val id: String,
    val name: String,
    val role: String,
    var status: AgentStatus = AgentStatus.IDLE,
    var currentTask: String? = null,
    val parentAgentId: String? = null,
)
