package dev.carecopilot.domain;

import java.time.Instant;

public record CareAuditEventView(
    String auditId,
    String caseId,
    String eventType,
    String actor,
    String summary,
    String agentRunId,
    String agentTaskJobId,
    String trainability,
    Instant occurredAt) {
}
