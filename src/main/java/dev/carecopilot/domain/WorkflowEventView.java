package dev.carecopilot.domain;

import java.time.Instant;

public record WorkflowEventView(
    String eventType,
    String message,
    String trainability,
    Instant occurredAt) {
}
