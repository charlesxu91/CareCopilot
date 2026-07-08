package dev.carecopilot.store;

public interface CareCopilotStateStore {
  CareCopilotState load();

  void save(CareCopilotState state);
}
