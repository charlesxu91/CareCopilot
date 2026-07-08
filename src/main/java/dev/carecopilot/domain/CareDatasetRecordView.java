package dev.carecopilot.domain;

import java.time.Instant;

public record CareDatasetRecordView(
    String recordId,
    String caseId,
    String sourceEventType,
    String summary,
    String agentRunId,
    String agentTaskJobId,
    String trainability,
    Instant occurredAt) {
}
