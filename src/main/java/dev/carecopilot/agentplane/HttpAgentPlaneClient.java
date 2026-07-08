package dev.carecopilot.agentplane;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAgentPlaneClient implements AgentPlaneClient {
  private final RestClient restClient;

  public HttpAgentPlaneClient(
      RestClient.Builder restClientBuilder,
      @Value("${carecopilot.agentplane.base-url:http://127.0.0.1:18080}") String baseUrl,
      @Value("${carecopilot.agentplane.connect-timeout-ms:3000}") long connectTimeoutMs,
      @Value("${carecopilot.agentplane.read-timeout-ms:10000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = restClientBuilder.requestFactory(requestFactory).baseUrl(baseUrl).build();
  }

  @Override
  public AgentPlaneSession createSession(CreateAgentPlaneSessionRequest request) {
    ensureRuntimeProvider(request.runtimeProvider());
    return restClient.post()
        .uri("/agent-sessions")
        .body(request)
        .retrieve()
        .body(AgentPlaneSession.class);
  }

  @Override
  public AgentPlaneArtifact createArtifact(CreateAgentPlaneArtifactRequest request) {
    return restClient.post()
        .uri("/artifacts")
        .body(request)
        .retrieve()
        .body(AgentPlaneArtifact.class);
  }

  @Override
  public AgentPlaneRun createRun(CreateAgentPlaneRunRequest request) {
    return restClient.post()
        .uri("/agent-runs")
        .body(request)
        .retrieve()
        .body(AgentPlaneRun.class);
  }

  @Override
  public AgentPlaneEvent appendRunEvent(String runId, CreateAgentPlaneEventRequest request) {
    return restClient.post()
        .uri("/agent-runs/{runId}/events", runId)
        .body(request)
        .retrieve()
        .body(AgentPlaneEvent.class);
  }

  @Override
  public AgentPlaneTask submitAgentTask(SubmitAgentTaskRequest request) {
    return restClient.post()
        .uri("/jobs")
        .body(request)
        .retrieve()
        .body(AgentPlaneTask.class);
  }

  private void ensureRuntimeProvider(String runtimeProvider) {
    restClient.post()
        .uri("/runtime-providers")
        .body(Map.of(
            "runtimeProvider", runtimeProvider,
            "runtimeVersion", "2.0",
            "supportedEventTypes", List.of("MODEL_CALL", "TOOL_CALL", "SAFETY_CHECK"),
            "supportsSessionResume", true,
            "supportsToolPermissions", true,
            "supportsSandbox", true,
            "supportsMcp", false,
            "supportsOtel", true))
        .retrieve()
        .toBodilessEntity();
  }
}
