package dev.carecopilot.store;

public class NoopCareCopilotStateStore implements CareCopilotStateStore {
  @Override
  public CareCopilotState load() {
    return CareCopilotState.empty();
  }

  @Override
  public void save(CareCopilotState state) {
  }
}
