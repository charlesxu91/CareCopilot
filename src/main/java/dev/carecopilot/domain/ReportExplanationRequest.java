package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;

public record ReportExplanationRequest(
    @NotBlank String caseId,
    @NotBlank String reportText) {
}
