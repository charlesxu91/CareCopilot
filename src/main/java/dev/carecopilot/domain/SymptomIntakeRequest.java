package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;

public record SymptomIntakeRequest(
    @NotBlank String caseId,
    @NotBlank String chiefComplaint,
    @NotBlank String duration,
    @NotBlank String severity) {
}
