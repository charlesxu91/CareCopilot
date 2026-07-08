package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ChatConfirmRequest(
    @NotBlank String caseId,
    @NotBlank String intentId,
    @NotBlank String intent,
    @NotBlank String message,
    Map<String, String> extractedSlots) {
}
