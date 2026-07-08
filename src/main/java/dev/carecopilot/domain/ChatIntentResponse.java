package dev.carecopilot.domain;

import java.util.List;
import java.util.Map;

public record ChatIntentResponse(
    String language,
    String caseId,
    String intentId,
    String intent,
    String medicalTaskCandidate,
    double confidence,
    String title,
    String summary,
    Map<String, String> extractedSlots,
    SafetyView safety,
    boolean requiresConfirmation,
    String confirmationLabel,
    String answer,
    String agentMode,
    String agentRunId,
    String agentTaskJobId,
    String decisionTrace,
    List<WorkflowEventView> events) {
}
