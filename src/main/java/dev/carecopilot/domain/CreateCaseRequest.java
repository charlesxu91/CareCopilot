package dev.carecopilot.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateCaseRequest(
    @NotBlank String displayName,
    @Min(0) int age,
    @NotBlank String sex,
    List<String> allergies,
    List<String> medications) {
}
