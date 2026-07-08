package dev.carecopilot.agentplane;

import java.util.Map;

public record CreateAgentPlaneArtifactRequest(
    String tenantId,
    String artifactKind,
    String contentType,
    String trainability,
    String body,
    Map<String, String> metadata) {
}
