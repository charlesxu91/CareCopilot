package dev.carecopilot.domain;

import java.time.Instant;
import java.util.List;

public record CaseView(
    String caseId,
    String agentSessionId,
    String displayName,
    int age,
    String sex,
    List<String> allergies,
    List<String> medications,
    Instant createdAt) {
}
