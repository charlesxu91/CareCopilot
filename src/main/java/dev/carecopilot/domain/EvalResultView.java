package dev.carecopilot.domain;

import java.util.List;

public record EvalResultView(
    String status,
    int totalCases,
    int passedCases,
    List<EvalCheckView> checks) {
}
