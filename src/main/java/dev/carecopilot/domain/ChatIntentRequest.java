package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;

public record ChatIntentRequest(
    @NotBlank String caseId,
    @NotBlank String message) {
}
