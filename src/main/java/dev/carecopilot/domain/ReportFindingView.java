package dev.carecopilot.domain;

public record ReportFindingView(
    String name,
    String value,
    String direction,
    String explanation) {
}
