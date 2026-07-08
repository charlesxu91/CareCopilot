package dev.carecopilot.store;

import dev.carecopilot.domain.CareAuditEventView;
import dev.carecopilot.domain.CaseView;
import dev.carecopilot.domain.TimelineEntryView;
import java.util.List;
import java.util.Map;

public record CareCopilotState(
    Map<String, CaseView> cases,
    Map<String, List<TimelineEntryView>> timelines,
    List<CareAuditEventView> auditEvents) {
  public static CareCopilotState empty() {
    return new CareCopilotState(Map.of(), Map.of(), List.of());
  }
}
