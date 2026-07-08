package dev.carecopilot.agentplane;

import java.util.Map;

public record SubmitAgentTaskRequest(
    String workloadType,
    int priority,
    int requiredGpuCount,
    long requiredMemoryMiB,
    Map<String, Object> payload) {
}
