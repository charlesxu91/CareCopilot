package dev.carecopilot.domain;

import java.util.List;

public record StructuredCarePlanView(
    String triageLevel,
    List<String> recommendedActions,
    List<String> followUpQuestions,
    List<String> boundaries) {
}
