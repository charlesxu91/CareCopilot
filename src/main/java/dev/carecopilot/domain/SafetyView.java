package dev.carecopilot.domain;

import java.util.List;

public record SafetyView(
    boolean redFlag,
    String decision,
    List<String> reasons) {
}
