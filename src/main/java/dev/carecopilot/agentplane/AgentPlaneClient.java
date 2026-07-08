package dev.carecopilot.agentplane;

public interface AgentPlaneClient {
  AgentPlaneSession createSession(CreateAgentPlaneSessionRequest request);

  AgentPlaneArtifact createArtifact(CreateAgentPlaneArtifactRequest request);

  AgentPlaneRun createRun(CreateAgentPlaneRunRequest request);

  AgentPlaneEvent appendRunEvent(String runId, CreateAgentPlaneEventRequest request);

  AgentPlaneTask submitAgentTask(SubmitAgentTaskRequest request);
}
