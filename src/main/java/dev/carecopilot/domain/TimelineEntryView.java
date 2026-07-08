package dev.carecopilot.domain;

import java.time.Instant;

public record TimelineEntryView(
    String entryId,
    String caseId,
    String eventType,
    String title,
    String summary,
    String agentRunId,
    String agentTaskJobId,
    Instant occurredAt) {
}
