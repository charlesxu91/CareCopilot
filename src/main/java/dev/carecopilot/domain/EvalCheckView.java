package dev.carecopilot.domain;

public record EvalCheckView(
    String name,
    boolean passed,
    String message) {
}
