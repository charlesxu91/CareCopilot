package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;

public record ClearChatContextRequest(
    @NotBlank String caseId) {
}
