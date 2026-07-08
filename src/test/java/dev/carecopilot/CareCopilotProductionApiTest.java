package dev.carecopilot;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.carecopilot.agentplane.AgentPlaneArtifact;
import dev.carecopilot.agentplane.AgentPlaneClient;
import dev.carecopilot.agentplane.AgentPlaneRun;
import dev.carecopilot.agentplane.AgentPlaneSession;
import dev.carecopilot.agentplane.AgentPlaneTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CareCopilotProductionApiTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AgentPlaneClient agentPlaneClient;

  @BeforeEach
  void setUpAgentPlane() {
    when(agentPlaneClient.createSession(any())).thenReturn(new AgentPlaneSession("agentplane-session-prod"));
    when(agentPlaneClient.createArtifact(any())).thenReturn(new AgentPlaneArtifact("agentplane-artifact-prod"));
    when(agentPlaneClient.createRun(any())).thenReturn(new AgentPlaneRun("agentplane-run-prod"));
    when(agentPlaneClient.appendRunEvent(any(), any())).thenReturn(null);
    when(agentPlaneClient.submitAgentTask(any())).thenReturn(new AgentPlaneTask("agentplane-job-prod"));
  }

  @Test
  void returnsStructuredClientErrorForUnknownCase() throws Exception {
    mockMvc.perform(get("/cases/case-does-not-exist"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Unknown case: case-does-not-exist"))
        .andExpect(jsonPath("$.path").value("/cases/case-does-not-exist"))
        .andExpect(jsonPath("$.timestamp", notNullValue()));
  }

  @Test
  void exposesPrometheusMetricsEndpoint() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_memory_used_bytes")));
  }

  @Test
  void recordsAuditEventsAndExportsTrainableJsonl() throws Exception {
    MvcResult createdCase = mockMvc.perform(post("/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "displayName": "生产用户",
              "age": 42,
              "sex": "未知",
              "allergies": [],
              "medications": ["阿托伐他汀"]
            }
            """))
        .andExpect(status().isOk())
        .andReturn();

    String caseId = JsonTestSupport.extractString(createdCase.getResponse().getContentAsString(), "caseId");

    mockMvc.perform(post("/workflows/symptom-intake")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "chiefComplaint": "胸痛并伴有出汗",
              "duration": "10分钟",
              "severity": "较重"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.safety.redFlag").value(true));

    mockMvc.perform(get("/cases/{caseId}/audit-events", caseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId))
        .andExpect(jsonPath("$.events", hasSize(2)))
        .andExpect(jsonPath("$.events[*].eventType", hasItem("CASE_CREATED")))
        .andExpect(jsonPath("$.events[*].eventType", hasItem("SYMPTOM_INTAKE")));

    mockMvc.perform(post("/datasets/export/jsonl")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "tenantId": "tenant-carecopilot-dev",
              "allowedTrainability": ["TRAINABLE_TELEMETRY", "TRAINABLE_REDACTED_TEXT"]
            }
            """))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("\"caseId\":\"" + caseId + "\"")))
        .andExpect(content().string(containsString("\"sourceEventType\":\"SYMPTOM_INTAKE\"")));
  }
}
