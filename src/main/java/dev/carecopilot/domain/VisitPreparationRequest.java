package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;

public record VisitPreparationRequest(
    @NotBlank String caseId,
    @NotBlank String visitGoal) {
}
