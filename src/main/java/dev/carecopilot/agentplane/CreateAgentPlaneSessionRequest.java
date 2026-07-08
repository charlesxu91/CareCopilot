package dev.carecopilot.agentplane;

public record CreateAgentPlaneSessionRequest(
    String tenantId,
    String runtimeProvider,
    String runtimeSessionId,
    String conversationId) {
}
