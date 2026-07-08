package dev.carecopilot.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CareDatasetExportRequest(
    @NotBlank String tenantId,
    @NotEmpty List<String> allowedTrainability) {
}
