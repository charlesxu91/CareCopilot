package dev.carecopilot.domain;

import java.util.List;

public record CaseTimelineView(
    String caseId,
    List<TimelineEntryView> entries) {
}
