package dev.carecopilot.agentplane;

public record CreateAgentPlaneEventRequest(
    long sequence,
    String eventType,
    String source,
    String message,
    String toolName,
    String toolCallId,
    String artifactRef,
    long latencyMs,
    String error,
    String trainability) {
}
