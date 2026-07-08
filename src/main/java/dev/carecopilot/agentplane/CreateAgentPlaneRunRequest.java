package dev.carecopilot.agentplane;

public record CreateAgentPlaneRunRequest(
    String sessionId,
    String agentName,
    String inputArtifactRef,
    String requestedBy) {
}
