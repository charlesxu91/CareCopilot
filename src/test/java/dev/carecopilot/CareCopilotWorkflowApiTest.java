package dev.carecopilot;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.carecopilot.agentplane.AgentPlaneClient;
import dev.carecopilot.agentplane.AgentPlaneSession;
import dev.carecopilot.agentplane.AgentPlaneArtifact;
import dev.carecopilot.agentplane.AgentPlaneRun;
import dev.carecopilot.agentplane.AgentPlaneTask;
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
class CareCopilotWorkflowApiTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AgentPlaneClient agentPlaneClient;

  @Test
  void createsCaseAndRunsChineseSafetyFirstWorkflows() throws Exception {
    when(agentPlaneClient.createSession(any()))
        .thenReturn(new AgentPlaneSession("agentplane-session-001"));
    when(agentPlaneClient.createArtifact(any()))
        .thenReturn(new AgentPlaneArtifact("agentplane-artifact-001"));
    when(agentPlaneClient.createRun(any()))
        .thenReturn(new AgentPlaneRun("agentplane-run-001"));
    when(agentPlaneClient.appendRunEvent(any(), any()))
        .thenReturn(null);
    when(agentPlaneClient.submitAgentTask(any()))
        .thenReturn(new AgentPlaneTask("agentplane-job-001"));

    MvcResult createdCase = mockMvc.perform(post("/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "displayName": "测试用户",
              "age": 36,
              "sex": "未知",
              "allergies": ["青霉素"],
              "medications": ["二甲双胍"]
            }
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId", notNullValue()))
        .andExpect(jsonPath("$.agentSessionId").value("agentplane-session-001"))
        .andReturn();

    String caseId = JsonTestSupport.extractString(createdCase.getResponse().getContentAsString(), "caseId");

    mockMvc.perform(get("/cases/{caseId}", caseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId))
        .andExpect(jsonPath("$.displayName").value("测试用户"));

    mockMvc.perform(post("/workflows/symptom-intake")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "chiefComplaint": "胸痛并伴有出汗，持续20分钟",
              "duration": "20分钟",
              "severity": "较重"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("zh-CN"))
        .andExpect(jsonPath("$.workflow").value("SYMPTOM_INTAKE"))
        .andExpect(jsonPath("$.safety.redFlag").value(true))
        .andExpect(jsonPath("$.answer").value(containsString("尽快")))
        .andExpect(jsonPath("$.structured.triageLevel").value("URGENT"))
        .andExpect(jsonPath("$.structured.recommendedActions", hasSize(3)))
        .andExpect(jsonPath("$.structured.followUpQuestions", hasSize(3)))
        .andExpect(jsonPath("$.agentRunId").value("agentplane-run-001"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-001"))
        .andExpect(jsonPath("$.events", hasSize(2)))
        .andExpect(jsonPath("$.events[0].eventType").value("SAFETY_CHECK"));

    mockMvc.perform(post("/workflows/symptom-intake")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "chiefComplaint": "我想把二甲双胍停掉，最近胃不舒服",
              "duration": "3天",
              "severity": "中等"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.safety.redFlag").value(false))
        .andExpect(jsonPath("$.safety.decision").value("MEDICATION_BOUNDARY"))
        .andExpect(jsonPath("$.answer").value(containsString("不要自行停药")))
        .andExpect(jsonPath("$.structured.boundaries", hasItem("MEDICATION_CHANGE_REQUIRES_CLINICIAN")));

    mockMvc.perform(post("/workflows/report-explanation")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "reportText": "血红蛋白 95 g/L ↓；白细胞 6.0 x10^9/L；空腹血糖 8.1 mmol/L ↑"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflow").value("REPORT_EXPLANATION"))
        .andExpect(jsonPath("$.abnormalItems", hasSize(2)))
        .andExpect(jsonPath("$.reportFindings", hasSize(2)))
        .andExpect(jsonPath("$.reportFindings[0].direction").value("LOW"))
        .andExpect(jsonPath("$.reportFindings[1].direction").value("HIGH"))
        .andExpect(jsonPath("$.answer").value(containsString("仅供理解报告")));

    mockMvc.perform(post("/workflows/visit-preparation")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "visitGoal": "复诊时想确认血糖控制和贫血问题"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflow").value("VISIT_PREPARATION"))
        .andExpect(jsonPath("$.questions", hasSize(3)))
        .andExpect(jsonPath("$.documentsToBring", hasItem("近期检查报告和影像资料")))
        .andExpect(jsonPath("$.answer").value(containsString("就诊前")));

    mockMvc.perform(get("/cases/{caseId}/timeline", caseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId))
        .andExpect(jsonPath("$.entries[*].eventType", hasItem("CASE_CREATED")))
        .andExpect(jsonPath("$.entries[*].eventType", hasItem("SYMPTOM_INTAKE")))
        .andExpect(jsonPath("$.entries[*].eventType", hasItem("MEDICATION_BOUNDARY")))
        .andExpect(jsonPath("$.entries[*].eventType", hasItem("REPORT_EXPLANATION")))
        .andExpect(jsonPath("$.entries[*].eventType", hasItem("VISIT_PREPARATION")));

    mockMvc.perform(get("/evals/carecopilot/mvp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PASS"))
        .andExpect(jsonPath("$.totalCases").value(5))
        .andExpect(jsonPath("$.passedCases").value(5))
        .andExpect(jsonPath("$.checks", hasSize(5)));

    verify(agentPlaneClient).createSession(any());
    verify(agentPlaneClient, atLeastOnce()).createArtifact(any());
    verify(agentPlaneClient, atLeastOnce()).createRun(any());
    verify(agentPlaneClient, atLeastOnce()).submitAgentTask(any());
  }

  @Test
  void modelAndAgentQuestionsUseGeneralChatInsteadOfAssistantInfoBranch() throws Exception {
    when(agentPlaneClient.createSession(any()))
        .thenReturn(new AgentPlaneSession("agentplane-session-info"));
    when(agentPlaneClient.createArtifact(any()))
        .thenReturn(new AgentPlaneArtifact("agentplane-artifact-info"));
    when(agentPlaneClient.createRun(any()))
        .thenReturn(new AgentPlaneRun("agentplane-run-info"));
    when(agentPlaneClient.appendRunEvent(any(), any()))
        .thenReturn(null);
    when(agentPlaneClient.submitAgentTask(any()))
        .thenReturn(new AgentPlaneTask("agentplane-job-info"));

    MvcResult createdCase = mockMvc.perform(post("/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "displayName": "测试用户",
              "age": 36,
              "sex": "未知",
              "allergies": [],
              "medications": []
            }
            """))
        .andExpect(status().isOk())
        .andReturn();

    String caseId = JsonTestSupport.extractString(createdCase.getResponse().getContentAsString(), "caseId");

    mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "你是什么模型"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("GENERAL_CHAT"))
        .andExpect(jsonPath("$.requiresConfirmation").value(false))
        .andExpect(jsonPath("$.safety.decision").value("ALLOW_GENERAL_CHAT"))
        .andExpect(jsonPath("$.agentMode").value("AGENTSCOPE_REACT"))
        .andExpect(jsonPath("$.agentRunId").value("agentplane-run-info"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-info"))
        .andExpect(jsonPath("$.decisionTrace").value(containsString("CareCopilotOrchestratorAgent")))
        .andExpect(jsonPath("$.decisionTrace").value(containsString("ANSWER_GENERAL_CHAT")))
        .andExpect(jsonPath("$.title").value("普通对话"))
        .andExpect(jsonPath("$.answer").doesNotExist());

    verify(agentPlaneClient).createSession(any());
    verify(agentPlaneClient).createArtifact(any());
    verify(agentPlaneClient).createRun(any());
    verify(agentPlaneClient).submitAgentTask(any());
  }

  @Test
  void nonMedicalChatDoesNotForceMedicalConfirmationCard() throws Exception {
    when(agentPlaneClient.createSession(any()))
        .thenReturn(new AgentPlaneSession("agentplane-session-general"));
    when(agentPlaneClient.createArtifact(any()))
        .thenReturn(new AgentPlaneArtifact("agentplane-artifact-general"));
    when(agentPlaneClient.createRun(any()))
        .thenReturn(new AgentPlaneRun("agentplane-run-general"));
    when(agentPlaneClient.appendRunEvent(any(), any()))
        .thenReturn(null);
    when(agentPlaneClient.submitAgentTask(any()))
        .thenReturn(new AgentPlaneTask("agentplane-job-general"));

    MvcResult createdCase = mockMvc.perform(post("/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "displayName": "测试用户",
              "age": 36,
              "sex": "未知",
              "allergies": [],
              "medications": []
            }
            """))
        .andExpect(status().isOk())
        .andReturn();

    String caseId = JsonTestSupport.extractString(createdCase.getResponse().getContentAsString(), "caseId");

    mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "你好"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("GENERAL_CHAT"))
        .andExpect(jsonPath("$.requiresConfirmation").value(false))
        .andExpect(jsonPath("$.answer").value(containsString("你好")))
        .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(containsString("我还不确定"))))
        .andExpect(jsonPath("$.decisionTrace").value(containsString("ANSWER_GENERAL_CHAT")));

    mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "你的Agent是什么模式"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("GENERAL_CHAT"))
        .andExpect(jsonPath("$.requiresConfirmation").value(false))
        .andExpect(jsonPath("$.answer").doesNotExist())
        .andExpect(jsonPath("$.decisionTrace").value(containsString("ANSWER_GENERAL_CHAT")));

    mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "qwen3是哪家公司开源的"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("GENERAL_CHAT"))
        .andExpect(jsonPath("$.requiresConfirmation").value(false))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-general"))
        .andExpect(jsonPath("$.answer").doesNotExist())
        .andExpect(jsonPath("$.decisionTrace").value(containsString("ANSWER_GENERAL_CHAT")));
  }

  @Test
  void medicalIntentConfirmationStartsMultiTurnInquiryBeforeExecutingAgentJob() throws Exception {
    when(agentPlaneClient.createSession(any()))
        .thenReturn(new AgentPlaneSession("agentplane-session-chat"));
    when(agentPlaneClient.createArtifact(any()))
        .thenReturn(new AgentPlaneArtifact("agentplane-artifact-chat"));
    when(agentPlaneClient.createRun(any()))
        .thenReturn(new AgentPlaneRun("agentplane-run-chat"));
    when(agentPlaneClient.appendRunEvent(any(), any()))
        .thenReturn(null);
    when(agentPlaneClient.submitAgentTask(any()))
        .thenReturn(new AgentPlaneTask("agentplane-job-chat"));

    MvcResult createdCase = mockMvc.perform(post("/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "displayName": "测试用户",
              "age": 36,
              "sex": "未知",
              "allergies": [],
              "medications": []
            }
            """))
        .andExpect(status().isOk())
        .andReturn();
    String caseId = JsonTestSupport.extractString(createdCase.getResponse().getContentAsString(), "caseId");

    MvcResult firstIntent = mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "胸痛并伴有出汗"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("MEDICAL_RELATED"))
        .andExpect(jsonPath("$.medicalTaskCandidate").value("SYMPTOM_INTAKE"))
        .andExpect(jsonPath("$.agentMode").value("AGENTSCOPE_REACT"))
        .andExpect(jsonPath("$.agentRunId").value("agentplane-run-chat"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-chat"))
        .andExpect(jsonPath("$.decisionTrace").value(containsString("CareCopilotOrchestratorAgent")))
        .andExpect(jsonPath("$.events[*].eventType", hasItem("AGENT_PLAN_CREATED")))
        .andExpect(jsonPath("$.requiresConfirmation").value(true))
        .andExpect(jsonPath("$.extractedSlots.chiefComplaint").value("胸痛并伴有出汗"))
        .andReturn();

    String firstIntentId = JsonTestSupport.extractString(firstIntent.getResponse().getContentAsString(), "intentId");

    mockMvc.perform(post("/chat/confirm")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "intentId": "%s",
              "intent": "SYMPTOM_INTAKE",
              "message": "胸痛并伴有出汗",
              "extractedSlots": {"chiefComplaint": "胸痛并伴有出汗", "duration": "当前对话", "severity": "较重", "_action": "START_CONTEXT"}
            }
            """.formatted(caseId, firstIntentId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflow").value("SYMPTOM_INTAKE_CONTEXT"))
        .andExpect(jsonPath("$.answer").value(containsString("已进入症状分诊问询")))
        .andExpect(jsonPath("$.agentRunId").value("agentplane-run-chat"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-chat"))
        .andExpect(jsonPath("$.events[*].eventType", hasItem("AGENT_PLAN_CREATED")));

    mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "持续20分钟，程度较重"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("MEDICAL_RELATED"))
        .andExpect(jsonPath("$.medicalTaskCandidate").value("SYMPTOM_INTAKE"))
        .andExpect(jsonPath("$.agentMode").value("AGENTSCOPE_REACT"))
        .andExpect(jsonPath("$.agentRunId").value("agentplane-run-chat"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-chat"))
        .andExpect(jsonPath("$.requiresConfirmation").value(false))
        .andExpect(jsonPath("$.answer").value(containsString("信息补充完后")));

    MvcResult readyIntent = mockMvc.perform(post("/chat/intent")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "message": "可以生成建议"
            }
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("MEDICAL_RELATED"))
        .andExpect(jsonPath("$.medicalTaskCandidate").value("SYMPTOM_INTAKE"))
        .andExpect(jsonPath("$.requiresConfirmation").value(true))
        .andExpect(jsonPath("$.confirmationLabel").value("确认，生成建议"))
        .andReturn();

    String readyIntentId = JsonTestSupport.extractString(readyIntent.getResponse().getContentAsString(), "intentId");

    mockMvc.perform(post("/chat/confirm")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "caseId": "%s",
              "intentId": "%s",
              "intent": "SYMPTOM_INTAKE",
              "message": "可以生成建议",
              "extractedSlots": {"chiefComplaint": "胸痛并伴有出汗\\n持续20分钟，程度较重", "duration": "当前对话", "severity": "较重", "_action": "EXECUTE"}
            }
            """.formatted(caseId, readyIntentId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflow").value("SYMPTOM_INTAKE"))
        .andExpect(jsonPath("$.agentTaskJobId").value("agentplane-job-chat"));

    mockMvc.perform(post("/chat/context/clear")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"caseId": "%s"}
            """.formatted(caseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLEARED"));

    verify(agentPlaneClient).createSession(any());
    verify(agentPlaneClient, atLeastOnce()).createArtifact(any());
    verify(agentPlaneClient, atLeastOnce()).createRun(any());
    verify(agentPlaneClient, atLeastOnce()).submitAgentTask(any());
  }
}
