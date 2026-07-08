package dev.carecopilot.domain;

import java.util.List;

public record CaseAuditEventsView(
    String caseId,
    List<CareAuditEventView> events) {
}
