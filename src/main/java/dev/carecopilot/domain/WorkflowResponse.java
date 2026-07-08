package dev.carecopilot.domain;

import java.util.List;

public record WorkflowResponse(
    String language,
    String workflow,
    String caseId,
    String agentSessionId,
    String agentRunId,
    String agentTaskJobId,
    String answer,
    SafetyView safety,
    List<String> abnormalItems,
    List<String> questions,
    StructuredCarePlanView structured,
    List<ReportFindingView> reportFindings,
    List<String> documentsToBring,
    List<WorkflowEventView> events) {
}
