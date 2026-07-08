package dev.carecopilot.domain;

public record ChatContextResponse(
    String language,
    String caseId,
    String status,
    String answer) {
}
