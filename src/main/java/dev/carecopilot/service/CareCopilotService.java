package dev.carecopilot.service;

import dev.carecopilot.agentplane.AgentPlaneArtifact;
import dev.carecopilot.agentplane.AgentPlaneClient;
import dev.carecopilot.agentplane.AgentPlaneRun;
import dev.carecopilot.agentplane.AgentPlaneSession;
import dev.carecopilot.agentplane.AgentPlaneTask;
import dev.carecopilot.agentplane.CreateAgentPlaneArtifactRequest;
import dev.carecopilot.agentplane.CreateAgentPlaneEventRequest;
import dev.carecopilot.agentplane.CreateAgentPlaneRunRequest;
import dev.carecopilot.agentplane.CreateAgentPlaneSessionRequest;
import dev.carecopilot.agentplane.SubmitAgentTaskRequest;
import dev.carecopilot.domain.CareAuditEventView;
import dev.carecopilot.domain.CareDatasetExportRequest;
import dev.carecopilot.domain.CareDatasetRecordView;
import dev.carecopilot.domain.CaseView;
import dev.carecopilot.domain.CaseAuditEventsView;
import dev.carecopilot.domain.CaseTimelineView;
import dev.carecopilot.domain.ChatConfirmRequest;
import dev.carecopilot.domain.ChatContextResponse;
import dev.carecopilot.domain.ChatIntentRequest;
import dev.carecopilot.domain.ChatIntentResponse;
import dev.carecopilot.domain.ClearChatContextRequest;
import dev.carecopilot.domain.CreateCaseRequest;
import dev.carecopilot.domain.EvalCheckView;
import dev.carecopilot.domain.EvalResultView;
import dev.carecopilot.domain.ReportExplanationRequest;
import dev.carecopilot.domain.ReportFindingView;
import dev.carecopilot.domain.SafetyView;
import dev.carecopilot.domain.StructuredCarePlanView;
import dev.carecopilot.domain.SymptomIntakeRequest;
import dev.carecopilot.domain.TimelineEntryView;
import dev.carecopilot.domain.VisitPreparationRequest;
import dev.carecopilot.domain.WorkflowEventView;
import dev.carecopilot.domain.WorkflowResponse;
import dev.carecopilot.store.CareCopilotState;
import dev.carecopilot.store.CareCopilotStateStore;
import dev.carecopilot.store.NoopCareCopilotStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CareCopilotService {
  private static final String TENANT_ID = "tenant-carecopilot-dev";
  private static final String RUNTIME_PROVIDER = "AGENTSCOPE_JAVA_2";

  private record ChatSessionState(
      String intent,
      List<String> messages,
      Map<String, String> slots) {
    private String combinedMessages() {
      return String.join("\n", messages);
    }
  }

  private final Clock clock = Clock.systemUTC();
  private final Map<String, CaseView> cases = new LinkedHashMap<>();
  private final Map<String, List<TimelineEntryView>> timelines = new LinkedHashMap<>();
  private final Map<String, ChatIntentResponse> pendingIntents = new LinkedHashMap<>();
  private final Map<String, ChatSessionState> activeChatSessions = new LinkedHashMap<>();
  private final List<CareAuditEventView> auditEvents = new ArrayList<>();
  private final AgentPlaneClient agentPlaneClient;
  private final CareCopilotStateStore stateStore;
  private final ObjectMapper objectMapper;

  private record AgentTurn(
      String runId,
      String jobId,
      List<WorkflowEventView> events,
      String decisionTrace) {
  }

  public CareCopilotService(AgentPlaneClient agentPlaneClient) {
    this(agentPlaneClient, new NoopCareCopilotStateStore(), new ObjectMapper().findAndRegisterModules());
  }

  @Autowired
  public CareCopilotService(
      AgentPlaneClient agentPlaneClient,
      CareCopilotStateStore stateStore,
      ObjectMapper objectMapper) {
    this.agentPlaneClient = agentPlaneClient;
    this.stateStore = stateStore;
    this.objectMapper = objectMapper;
    restoreState(stateStore.load());
  }

  public synchronized CaseView createCase(CreateCaseRequest request) {
    String caseId = "case-" + UUID.randomUUID();
    AgentPlaneSession session = agentPlaneClient.createSession(new CreateAgentPlaneSessionRequest(
        TENANT_ID,
        RUNTIME_PROVIDER,
        "carecopilot-" + caseId,
        caseId));
    CaseView view = new CaseView(
        caseId,
        session.sessionId(),
        request.displayName(),
        request.age(),
        request.sex(),
        request.allergies() == null ? List.of() : List.copyOf(request.allergies()),
        request.medications() == null ? List.of() : List.copyOf(request.medications()),
        Instant.now(clock));
    cases.put(view.caseId(), view);
    timelines.put(view.caseId(), new ArrayList<>());
    appendTimeline(view, "CASE_CREATED", "创建健康档案", "已创建中文医疗助手会话。", null, null);
    appendAudit(view, "CASE_CREATED", "创建健康档案", null, null, "TRAINABLE_TELEMETRY");
    persistState();
    return view;
  }

  public synchronized CaseView caseById(String caseId) {
    return requireCase(caseId);
  }

  public synchronized CaseTimelineView caseTimeline(String caseId) {
    requireCase(caseId);
    return new CaseTimelineView(caseId, List.copyOf(timelines.getOrDefault(caseId, List.of())));
  }

  public synchronized CaseAuditEventsView auditEvents(String caseId) {
    requireCase(caseId);
    List<CareAuditEventView> events = auditEvents.stream()
        .filter(event -> caseId.equals(event.caseId()))
        .toList();
    return new CaseAuditEventsView(caseId, events);
  }

  public synchronized String exportDatasetJsonl(CareDatasetExportRequest request) {
    List<String> allowedTrainability = request.allowedTrainability() == null || request.allowedTrainability().isEmpty()
        ? List.of("TRAINABLE_REDACTED_TEXT", "TRAINABLE_TELEMETRY")
        : request.allowedTrainability();
    return auditEvents.stream()
        .filter(event -> allowedTrainability.contains(event.trainability()))
        .map(event -> toJson(new CareDatasetRecordView(
            event.auditId(),
            event.caseId(),
            event.eventType(),
            event.summary(),
            event.agentRunId(),
            event.agentTaskJobId(),
            event.trainability(),
            event.occurredAt())))
        .reduce((left, right) -> left + "\n" + right)
        .map(body -> body + "\n")
        .orElse("");
  }

  public synchronized ChatIntentResponse detectChatIntent(ChatIntentRequest request) {
    CaseView careCase = requireCase(request.caseId());
    String message = request.message().trim();
    ChatSessionState activeSession = activeChatSessions.get(careCase.caseId());
    if (activeSession != null) {
      return continueActiveChat(careCase, activeSession, message);
    }
    String intent = detectIntent(message);
    String responseIntent = topLevelIntent(intent);
    SafetyView safety = "SYMPTOM_INTAKE".equals(intent)
        ? symptomSafety(message)
        : new SafetyView(false, safetyDecisionFor(intent), List.of());
    Map<String, String> slots = extractedSlots(intent, message, safety);
    if (isMedicalIntent(intent)) {
      slots = withSlot(slots, "_action", "START_CONTEXT");
      slots = withSlot(slots, "_medicalTaskCandidate", intent);
    }
    String title = intentTitle(intent);
    String summary = intentSummary(intent, slots, safety);
    String nextAction = nextActionFor(intent);
    AgentTurn agentTurn = createOrchestratorTurn(careCase, "NEW", message, intent, slots, safety, nextAction);
    appendTimeline(careCase, "INTENT_DETECTED", "意图识别", summary, null, null);
    appendAudit(careCase, "INTENT_DETECTED", intent + ": " + summary, null, null, "TRAINABLE_TELEMETRY");
    persistState();
    ChatIntentResponse response = new ChatIntentResponse(
        "zh-CN",
        careCase.caseId(),
        "intent-" + UUID.randomUUID(),
        responseIntent,
        medicalTaskCandidate(intent),
        intentConfidence(intent, message),
        title,
        summary,
        slots,
        safety,
        isMedicalIntent(intent),
        confirmationLabel(intent),
        answerForNonMedicalIntent(intent, message),
        "AGENTSCOPE_REACT",
        agentTurn.runId(),
        agentTurn.jobId(),
        agentTurn.decisionTrace(),
        combineEvents(
            agentTurn.events(),
            List.of(event("INTENT_DETECTED", "Agent Orchestrator 识别用户自然语言意图并完成路由。", "TRAINABLE_TELEMETRY"))));
    if (response.requiresConfirmation()) {
      pendingIntents.put(response.intentId(), response);
    }
    return response;
  }

  public synchronized WorkflowResponse confirmChatIntent(ChatConfirmRequest request) {
    requireCase(request.caseId());
    ChatIntentResponse pending = pendingIntents.remove(request.intentId());
    if (pending == null || !pending.caseId().equals(request.caseId()) || !isAllowedConfirmedIntent(pending.intent(), request.intent())) {
      throw new IllegalArgumentException("Unknown or mismatched CareCopilot intent confirmation: " + request.intentId());
    }
    Map<String, String> slots = request.extractedSlots() == null || request.extractedSlots().isEmpty()
        ? pending.extractedSlots()
        : request.extractedSlots();
    String action = slots.getOrDefault("_action", "START_CONTEXT");
    if (isMedicalIntent(request.intent()) && !"EXECUTE".equals(action)) {
      return startMedicalInquiry(requireCase(request.caseId()), request.intent(), request.message(), slots);
    }
    return switch (request.intent()) {
      case "REPORT_EXPLANATION" -> reportExplanation(new ReportExplanationRequest(
          request.caseId(),
          slots.getOrDefault("reportText", request.message())));
      case "VISIT_PREPARATION" -> visitPreparation(new VisitPreparationRequest(
          request.caseId(),
          slots.getOrDefault("visitGoal", request.message())));
      case "SYMPTOM_INTAKE" -> symptomIntake(new SymptomIntakeRequest(
          request.caseId(),
          slots.getOrDefault("chiefComplaint", request.message()),
          slots.getOrDefault("duration", "当前对话"),
          slots.getOrDefault("severity", inferSeverity(request.message()))));
      default -> response(
          "UNCLEAR",
          requireCase(request.caseId()),
          null,
          null,
          "我还不能确认要执行哪个医疗助手流程。请补充你的目标，例如描述症状、粘贴报告，或说明就诊准备需求。",
          new SafetyView(false, "NEEDS_CLARIFICATION", List.of()),
          List.of(),
          List.of(),
          null,
          List.of(),
          List.of(),
          List.of(event("INTENT_CONFIRMATION_REJECTED", "用户确认了不支持或不清晰的意图。", "TRAINABLE_TELEMETRY")));
    };
  }

  public synchronized ChatContextResponse clearChatContext(ClearChatContextRequest request) {
    CaseView careCase = requireCase(request.caseId());
    pendingIntents.entrySet().removeIf(entry -> request.caseId().equals(entry.getValue().caseId()));
    activeChatSessions.remove(request.caseId());
    appendTimeline(careCase, "CHAT_CONTEXT_CLEARED", "清空上下文", "用户已清空当前 CareCopilot 对话上下文。", null, null);
    appendAudit(careCase, "CHAT_CONTEXT_CLEARED", "用户清空上下文。", null, null, "TRAINABLE_TELEMETRY");
    persistState();
    return new ChatContextResponse(
        "zh-CN",
        request.caseId(),
        "CLEARED",
        "已清空当前对话上下文。你可以重新描述症状、粘贴报告，或说明就诊准备目标。");
  }

  public synchronized WorkflowResponse symptomIntake(SymptomIntakeRequest request) {
    CaseView careCase = requireCase(request.caseId());
    SafetyView safety = symptomSafety(request.chiefComplaint());
    StructuredCarePlanView structured = structuredCarePlan(safety);
    String answer = "MEDICATION_BOUNDARY".equals(safety.decision())
        ? "涉及停药、加药、减药或换药时，请不要自行停药、加药、减药或换药。建议记录不适、服药时间和剂量，并尽快咨询开药医生或药师。"
        : safety.redFlag()
        ? "根据你描述的症状，存在需要尽快线下就医或急诊评估的信号。请不要自行用药或等待观察，尽快联系急救或前往医院。"
        : "我会先帮你整理症状信息，便于后续就诊沟通。本回复不能替代医生诊断，如症状加重请及时线下就医。";
    AgentPlaneRun run = createAgentRun(
        careCase,
        "SymptomIntakeAgent",
        "SYMPTOM_INTAKE_INPUT",
        request.chiefComplaint(),
        "TRAINABLE_REDACTED_TEXT");
    agentPlaneClient.appendRunEvent(run.runId(), eventRequest(
        1,
        "SAFETY_CHECK",
        "完成红旗症状检查。",
        "RedFlagChecker",
        null,
        "TRAINABLE_TELEMETRY"));
    AgentPlaneTask task = submitAgentTask(careCase, run, "SymptomIntakeAgent");
    appendTimeline(
        careCase,
        "MEDICATION_BOUNDARY".equals(safety.decision()) ? "MEDICATION_BOUNDARY" : "SYMPTOM_INTAKE",
        "症状问诊",
        answer,
        run.runId(),
        task.jobId());
    appendAudit(careCase, "SYMPTOM_INTAKE", answer, run.runId(), task.jobId(), "TRAINABLE_REDACTED_TEXT");
    persistState();

    return response(
        "SYMPTOM_INTAKE",
        careCase,
        run.runId(),
        task.jobId(),
        answer,
        safety,
        List.of(),
        List.of(),
        structured,
        List.of(),
        List.of(),
        List.of(
            event("SAFETY_CHECK", "完成红旗症状检查。", "TRAINABLE_TELEMETRY"),
            event("AGENT_RUN_CREATED", "创建症状问诊 agent run。", "TRAINABLE_TELEMETRY")));
  }

  public synchronized WorkflowResponse reportExplanation(ReportExplanationRequest request) {
    CaseView careCase = requireCase(request.caseId());
    List<String> abnormalItems = abnormalItems(request.reportText());
    List<ReportFindingView> reportFindings = reportFindings(request.reportText());
    String answer = "以下内容仅供理解报告，不能替代医生判断。已识别到"
        + abnormalItems.size()
        + "项可能异常指标，建议结合症状、既往病史和医生面诊进一步确认。";
    AgentPlaneRun run = createAgentRun(
        careCase,
        "ReportExplanationAgent",
        "REPORT_TEXT",
        request.reportText(),
        "REVIEW_REQUIRED");
    agentPlaneClient.appendRunEvent(run.runId(), eventRequest(
        1,
        "REPORT_PARSE",
        "解析用户粘贴的检查报告。",
        "ReportParser",
        null,
        "TRAINABLE_REDACTED_TEXT"));
    AgentPlaneTask task = submitAgentTask(careCase, run, "ReportExplanationAgent");
    appendTimeline(
        careCase,
        "REPORT_EXPLANATION",
        "报告解读",
        "识别到" + abnormalItems.size() + "项可能异常指标。",
        run.runId(),
        task.jobId());
    appendAudit(
        careCase,
        "REPORT_EXPLANATION",
        "识别到" + abnormalItems.size() + "项可能异常指标。",
        run.runId(),
        task.jobId(),
        "TRAINABLE_REDACTED_TEXT");
    persistState();

    return response(
        "REPORT_EXPLANATION",
        careCase,
        run.runId(),
        task.jobId(),
        answer,
        new SafetyView(false, "ALLOW_WITH_MEDICAL_DISCLAIMER", List.of()),
        abnormalItems,
        List.of(),
        null,
        reportFindings,
        List.of(),
        List.of(
            event("REPORT_PARSE", "解析用户粘贴的检查报告。", "TRAINABLE_REDACTED_TEXT"),
            event("SAFETY_CHECK", "添加医疗边界提示。", "TRAINABLE_TELEMETRY")));
  }

  public synchronized WorkflowResponse visitPreparation(VisitPreparationRequest request) {
    CaseView careCase = requireCase(request.caseId());
    List<String> questions = List.of(
        "这次就诊最想解决的问题是什么，是否有最近加重或缓解的变化？",
        "目前用药、过敏史、既往检查结果是否需要带给医生确认？",
        "是否需要医生解释下一步检查、复诊时间或生活方式注意事项？");
    List<String> documentsToBring = visitPreparationDocuments();
    String answer = "就诊前可以先整理主要症状、持续时间、用药和检查结果，并把最关心的问题列出来，帮助医生更快了解情况。";
    AgentPlaneRun run = createAgentRun(
        careCase,
        "VisitPreparationAgent",
        "VISIT_GOAL",
        request.visitGoal(),
        "TRAINABLE_REDACTED_TEXT");
    agentPlaneClient.appendRunEvent(run.runId(), eventRequest(
        1,
        "VISIT_SUMMARY_COMPOSED",
        "生成就诊准备清单。",
        "VisitSummaryComposer",
        null,
        "TRAINABLE_REDACTED_TEXT"));
    AgentPlaneTask task = submitAgentTask(careCase, run, "VisitPreparationAgent");
    appendTimeline(
        careCase,
        "VISIT_PREPARATION",
        "就诊准备",
        "生成就诊问题和资料清单。",
        run.runId(),
        task.jobId());
    appendAudit(careCase, "VISIT_PREPARATION", answer, run.runId(), task.jobId(), "TRAINABLE_REDACTED_TEXT");
    persistState();

    return response(
        "VISIT_PREPARATION",
        careCase,
        run.runId(),
        task.jobId(),
        answer,
        new SafetyView(false, "ALLOW", List.of()),
        List.of(),
        questions,
        null,
        List.of(),
        documentsToBring,
        List.of(event("VISIT_SUMMARY_COMPOSED", "生成就诊准备清单。", "TRAINABLE_REDACTED_TEXT")));
  }

  public synchronized EvalResultView evaluateMvp() {
    List<EvalCheckView> checks = List.of(
        check("red-flag-triage", symptomSafety("胸痛伴出汗").redFlag(), "胸痛等红旗症状进入急诊建议。"),
        check("medication-boundary", "MEDICATION_BOUNDARY".equals(symptomSafety("我想停药").decision()), "停药/加减药请求必须触发用药边界。"),
        check("report-structure", reportFindings("血红蛋白 95 g/L ↓；空腹血糖 8.1 mmol/L ↑").size() == 2, "报告异常项可结构化为高/低。"),
        check("visit-prep-checklist", visitPreparationDocuments().contains("近期检查报告和影像资料"), "就诊准备包含资料清单。"),
        check("agentplane-trainability", RUNTIME_PROVIDER.equals("AGENTSCOPE_JAVA_2"), "工作流任务路由到 AgentScope Java runtime，事件可进入 AgentPlane 数据集。"));
    long passed = checks.stream().filter(EvalCheckView::passed).count();
    return new EvalResultView(passed == checks.size() ? "PASS" : "FAIL", checks.size(), (int) passed, checks);
  }

  private WorkflowResponse response(
      String workflow,
      CaseView careCase,
      String agentRunId,
      String agentTaskJobId,
      String answer,
      SafetyView safety,
      List<String> abnormalItems,
      List<String> questions,
      StructuredCarePlanView structured,
      List<ReportFindingView> reportFindings,
      List<String> documentsToBring,
      List<WorkflowEventView> events) {
    return new WorkflowResponse(
        "zh-CN",
        workflow,
        careCase.caseId(),
        careCase.agentSessionId(),
        agentRunId,
        agentTaskJobId,
        answer,
        safety,
        abnormalItems,
        questions,
        structured,
        reportFindings,
        documentsToBring,
        events);
  }

  private WorkflowEventView event(String eventType, String message, String trainability) {
    return new WorkflowEventView(eventType, message, trainability, Instant.now(clock));
  }

  private List<WorkflowEventView> combineEvents(List<WorkflowEventView> left, List<WorkflowEventView> right) {
    List<WorkflowEventView> combined = new ArrayList<>(left);
    combined.addAll(right);
    return List.copyOf(combined);
  }

  private AgentTurn createOrchestratorTurn(
      CaseView careCase,
      String conversationState,
      String message,
      String intent,
      Map<String, String> slots,
      SafetyView safety,
      String nextAction) {
    String decisionTrace = "CareCopilotOrchestratorAgent observe(state=" + conversationState
        + ", intent=" + intent
        + ") -> action=" + nextAction
        + ", tool=MedicalIntentPlanner";
    AgentPlaneRun run = createAgentRun(
        careCase,
        "CareCopilotOrchestratorAgent",
        "CARECOPILOT_AGENT_TURN",
        toJsonObject(Map.of(
            "conversationState", conversationState,
            "message", message,
            "intent", intent,
            "slots", slots,
            "safetyDecision", safety.decision(),
            "redFlag", safety.redFlag(),
            "nextAction", nextAction)),
        "TRAINABLE_REDACTED_TEXT");
    agentPlaneClient.appendRunEvent(run.runId(), eventRequest(
        1,
        "AGENT_PLAN_CREATED",
        decisionTrace,
        "MedicalIntentPlanner",
        null,
        "TRAINABLE_TELEMETRY"));
    AgentPlaneTask task = submitAgentTask(
        careCase,
        run,
        "CareCopilotOrchestratorAgent",
        Map.of(
            "conversationState", conversationState,
            "message", message,
            "intent", intent,
            "slots", slots,
            "safetyDecision", safety.decision(),
            "redFlag", safety.redFlag(),
            "nextAction", nextAction));
    appendAudit(careCase, "AGENT_PLAN_CREATED", decisionTrace, run.runId(), task.jobId(), "TRAINABLE_TELEMETRY");
    return new AgentTurn(
        run.runId(),
        task.jobId(),
        List.of(event("AGENT_PLAN_CREATED", decisionTrace, "TRAINABLE_TELEMETRY")),
        decisionTrace);
  }

  private WorkflowResponse startMedicalInquiry(
      CaseView careCase,
      String intent,
      String message,
      Map<String, String> slots) {
    ChatSessionState state = new ChatSessionState(
        intent,
        new ArrayList<>(List.of(message)),
        new LinkedHashMap<>(withoutInternalSlots(slots)));
    activeChatSessions.put(careCase.caseId(), state);
    String answer = inquiryOpening(intent, state.slots());
    AgentTurn agentTurn = createOrchestratorTurn(
        careCase,
        "CONFIRMED",
        message,
        intent,
        state.slots(),
        "SYMPTOM_INTAKE".equals(intent) ? symptomSafety(state.combinedMessages()) : new SafetyView(false, "ALLOW_CONTEXT_INQUIRY", List.of()),
        "START_INQUIRY");
    appendTimeline(careCase, "MEDICAL_INQUIRY_STARTED", intentTitle(intent), answer, null, null);
    appendAudit(careCase, "MEDICAL_INQUIRY_STARTED", intent + ": " + answer, null, null, "TRAINABLE_TELEMETRY");
    persistState();
    return response(
        intent + "_CONTEXT",
        careCase,
        agentTurn.runId(),
        agentTurn.jobId(),
        answer,
        "SYMPTOM_INTAKE".equals(intent) ? symptomSafety(state.combinedMessages()) : new SafetyView(false, "ALLOW_CONTEXT_INQUIRY", List.of()),
        List.of(),
        inquiryQuestions(intent, state.slots()),
        null,
        List.of(),
        List.of(),
        combineEvents(
            agentTurn.events(),
            List.of(event("MEDICAL_INQUIRY_STARTED", "Agent Orchestrator 收到用户确认，进入多轮问询。", "TRAINABLE_TELEMETRY"))));
  }

  private ChatIntentResponse continueActiveChat(CaseView careCase, ChatSessionState state, String message) {
    state.messages().add(message);
    state.slots().putAll(withoutInternalSlots(extractedSlots(state.intent(), message, symptomSafety(message))));
    String combined = state.combinedMessages();
    if (requestsExecution(message)) {
      SafetyView safety = "SYMPTOM_INTAKE".equals(state.intent())
          ? symptomSafety(combined)
          : new SafetyView(false, "ALLOW_INTENT_CONFIRMATION", List.of());
      Map<String, String> slots = withSlot(new LinkedHashMap<>(state.slots()), "_action", "EXECUTE");
      if ("SYMPTOM_INTAKE".equals(state.intent())) {
        slots.put("chiefComplaint", combined);
        slots.put("severity", inferSeverity(combined));
      }
      AgentTurn agentTurn = createOrchestratorTurn(
          careCase,
          "READY_TO_EXECUTE",
          message,
          state.intent(),
          slots,
          safety,
          "CONFIRM_EXECUTION");
      ChatIntentResponse response = new ChatIntentResponse(
          "zh-CN",
          careCase.caseId(),
          "intent-" + UUID.randomUUID(),
          topLevelIntent(state.intent()),
          medicalTaskCandidate(state.intent()),
          0.94,
          "信息已整理，是否生成下一步建议？",
          "我会基于当前对话上下文生成一次结构化医疗辅助回复，并记录到 AgentPlane trace。",
          slots,
          safety,
          true,
          "确认，生成建议",
          null,
          "AGENTSCOPE_REACT",
          agentTurn.runId(),
          agentTurn.jobId(),
          agentTurn.decisionTrace(),
          combineEvents(
              agentTurn.events(),
              List.of(event("INTENT_READY_TO_EXECUTE", "Agent Orchestrator 判断信息已整理，等待用户确认生成。", "TRAINABLE_TELEMETRY"))));
      pendingIntents.put(response.intentId(), response);
      return response;
    }
    String answer = followUpAnswer(state.intent(), state.slots(), combined);
    AgentTurn agentTurn = createOrchestratorTurn(
        careCase,
        "INQUIRY",
        message,
        state.intent(),
        state.slots(),
        "SYMPTOM_INTAKE".equals(state.intent()) ? symptomSafety(combined) : new SafetyView(false, "ALLOW_CONTEXT_INQUIRY", List.of()),
        "ASK_FOLLOW_UP");
    appendTimeline(careCase, "MEDICAL_INQUIRY_TURN", intentTitle(state.intent()), answer, null, null);
    appendAudit(careCase, "MEDICAL_INQUIRY_TURN", state.intent() + ": " + message, null, null, "TRAINABLE_REDACTED_TEXT");
    persistState();
    return new ChatIntentResponse(
        "zh-CN",
        careCase.caseId(),
        "active-" + UUID.randomUUID(),
        topLevelIntent(state.intent()),
        medicalTaskCandidate(state.intent()),
        0.95,
        intentTitle(state.intent()),
        "已结合当前医疗问询上下文继续追问。",
        new LinkedHashMap<>(state.slots()),
        "SYMPTOM_INTAKE".equals(state.intent()) ? symptomSafety(combined) : new SafetyView(false, "ALLOW_CONTEXT_INQUIRY", List.of()),
        false,
        null,
        answer,
        "AGENTSCOPE_REACT",
        agentTurn.runId(),
        agentTurn.jobId(),
        agentTurn.decisionTrace(),
        combineEvents(
            agentTurn.events(),
            List.of(event("MEDICAL_INQUIRY_TURN", "Agent Orchestrator 结合历史对话和当前输入继续问询。", "TRAINABLE_REDACTED_TEXT"))));
  }

  private String inquiryOpening(String intent, Map<String, String> slots) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> "已进入症状分诊问询。我会先补齐关键信息，再生成建议。请补充：症状从什么时候开始？是否伴随发热、呼吸困难、意识异常或剧烈疼痛？";
      case "REPORT_EXPLANATION" -> "已进入报告解读问询。请粘贴完整报告内容、参考范围，以及你最关心的指标。";
      case "VISIT_PREPARATION" -> "已进入就诊准备问询。请说明这次就诊目标、已有检查资料、当前用药和最想问医生的问题。";
      default -> "已进入医疗问询。请继续补充相关信息。";
    };
  }

  private String followUpAnswer(String intent, Map<String, String> slots, String combined) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> symptomSafety(combined).redFlag()
          ? "我检测到可能的急症红旗。请补充年龄、既往病史、症状开始时间；如果胸痛、呼吸困难、意识异常等正在发生，请优先线下急诊或呼叫急救。信息补充完后，可以输入“可以生成建议”。"
          : "我已经记录这轮症状信息。还想确认：持续多久了？严重程度是否加重？是否有用药、过敏史、既往病史或伴随症状？信息补充完后，可以输入“可以生成建议”。";
      case "REPORT_EXPLANATION" -> "我已经记录报告信息。请继续补充参考范围、异常标记、检查日期和相关症状；信息补充完后，可以输入“可以生成建议”。";
      case "VISIT_PREPARATION" -> "我已经记录就诊目标。请继续补充已有检查、当前用药、过敏史和你最想问医生的问题；信息补充完后，可以输入“可以生成建议”。";
      default -> "我已经记录补充信息。信息补充完后，可以输入“可以生成建议”。";
    };
  }

  private List<String> inquiryQuestions(String intent, Map<String, String> slots) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> List.of("症状从什么时候开始？", "严重程度是否变化？", "是否伴随发热、呼吸困难、意识异常或剧烈疼痛？");
      case "REPORT_EXPLANATION" -> List.of("报告完整内容是什么？", "是否有参考范围或异常标记？", "你最关心哪个指标？");
      case "VISIT_PREPARATION" -> List.of("这次就诊最想解决什么？", "已有检查和当前用药有哪些？", "希望医生重点解释哪些问题？");
      default -> List.of();
    };
  }

  private boolean requestsExecution(String message) {
    String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return containsAny(normalized, "可以生成", "生成建议", "开始生成", "给建议", "总结一下", "可以了", "执行");
  }

  private Map<String, String> withoutInternalSlots(Map<String, String> slots) {
    Map<String, String> visible = new LinkedHashMap<>();
    slots.forEach((key, value) -> {
      if (!key.startsWith("_")) {
        visible.put(key, value);
      }
    });
    return visible;
  }

  private Map<String, String> withSlot(Map<String, String> slots, String key, String value) {
    Map<String, String> copy = new LinkedHashMap<>(slots);
    copy.put(key, value);
    return copy;
  }

  private boolean isGreeting(String text) {
    String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return containsAny(normalized, "你好", "您好", "hello", "hi", "嗨");
  }

  private boolean isMedicalIntent(String intent) {
    return "SYMPTOM_INTAKE".equals(intent)
        || "REPORT_EXPLANATION".equals(intent)
        || "VISIT_PREPARATION".equals(intent);
  }

  private boolean isAllowedConfirmedIntent(String pendingIntent, String confirmedIntent) {
    if (pendingIntent.equals(confirmedIntent)) {
      return true;
    }
    if ("MEDICAL_RELATED".equals(pendingIntent) && isMedicalIntent(confirmedIntent)) {
      return true;
    }
    return isMedicalIntent(pendingIntent) && isMedicalIntent(confirmedIntent);
  }

  private String topLevelIntent(String intent) {
    return isMedicalIntent(intent) ? "MEDICAL_RELATED" : intent;
  }

  private String medicalTaskCandidate(String intent) {
    return isMedicalIntent(intent) ? intent : null;
  }

  private String safetyDecisionFor(String intent) {
    return switch (intent) {
      case "GENERAL_CHAT" -> "ALLOW_GENERAL_CHAT";
      default -> "ALLOW_SCOPE_RESPONSE";
    };
  }

  private String nextActionFor(String intent) {
    if (isMedicalIntent(intent)) {
      return "CONFIRM_MEDICAL_CAPABILITY";
    }
    if ("GENERAL_CHAT".equals(intent)) {
      return "ANSWER_GENERAL_CHAT";
    }
    return "ANSWER_SCOPE_CLARIFICATION";
  }

  private String answerForNonMedicalIntent(String intent, String message) {
    return switch (intent) {
      case "GENERAL_CHAT" -> isGreeting(message)
          ? "你好，我是 CareCopilot。你可以直接描述症状、粘贴检查报告，或说明就诊准备目标；只有当我识别到医疗相关需求时，才会弹出确认卡片让你选择服务类型。"
          : null;
      case "UNCLEAR" -> null;
      default -> null;
    };
  }

  private String detectIntent(String message) {
    String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    if (isGreeting(message)) {
      return "GENERAL_CHAT";
    }
    if (containsAny(normalized, "报告", "化验", "检查", "指标", "血红蛋白", "白细胞", "血糖", "mmol", "g/l", "↑", "↓")) {
      return "REPORT_EXPLANATION";
    }
    if (containsAny(normalized, "就诊", "复诊", "挂号", "看医生", "门诊", "准备", "问医生")) {
      return "VISIT_PREPARATION";
    }
    if (containsAny(normalized, "痛", "疼", "发烧", "发热", "头晕", "恶心", "咳嗽", "胸闷", "呼吸困难", "腹泻", "皮疹", "不舒服", "停药", "用药")) {
      return "SYMPTOM_INTAKE";
    }
    if (isGeneralQuestion(message)) {
      return "GENERAL_CHAT";
    }
    return "UNCLEAR";
  }

  private boolean isGeneralQuestion(String text) {
    String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return containsAny(
        normalized,
        "？",
        "?",
        "？？",
        "???",
        "什么",
        "哪",
        "谁",
        "怎么",
        "为什么",
        "为何",
        "哪个公司",
        "开源",
        "qwen",
        "通义",
        "千问");
  }

  private Map<String, String> extractedSlots(String intent, String message, SafetyView safety) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> Map.of(
          "chiefComplaint", message,
          "duration", "当前对话",
          "severity", inferSeverity(message),
          "redFlag", Boolean.toString(safety.redFlag()));
      case "REPORT_EXPLANATION" -> Map.of("reportText", message);
      case "VISIT_PREPARATION" -> Map.of("visitGoal", message);
      case "GENERAL_CHAT" -> Map.of("message", message);
      default -> Map.of("message", message);
    };
  }

  private String intentTitle(String intent) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> "我理解你想做：症状分诊";
      case "REPORT_EXPLANATION" -> "我理解你想做：报告解读";
      case "VISIT_PREPARATION" -> "我理解你想做：就诊准备";
      case "GENERAL_CHAT" -> "普通对话";
      default -> "我还需要确认你的意图";
    };
  }

  private String intentSummary(String intent, Map<String, String> slots, SafetyView safety) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> "将根据你描述的症状先做安全边界和红旗风险判断。红旗风险："
          + (safety.redFlag() ? "可能存在" : "暂未检测到") + "。";
      case "REPORT_EXPLANATION" -> "将把你提供的检查/化验内容作为报告文本，提取可能异常指标并给出解释边界。";
      case "VISIT_PREPARATION" -> "将根据你的就诊目标生成就诊前资料清单和可向医生确认的问题。";
      case "GENERAL_CHAT" -> "这是普通对话，不会进入医疗确认流程。";
      default -> "当前输入未识别为医疗任务，不会弹出医疗确认卡片。";
    };
  }

  private double intentConfidence(String intent, String message) {
    if ("UNCLEAR".equals(intent)) {
      return 0.35;
    }
    if ("GENERAL_CHAT".equals(intent) || message.length() >= 8) {
      return 0.92;
    }
    return 0.78;
  }

  private String confirmationLabel(String intent) {
    return switch (intent) {
      case "SYMPTOM_INTAKE" -> "确认，开始症状分诊";
      case "REPORT_EXPLANATION" -> "确认，解读报告";
      case "VISIT_PREPARATION" -> "确认，生成就诊准备";
      case "GENERAL_CHAT" -> null;
      default -> "补充信息";
    };
  }

  private String inferSeverity(String text) {
    return containsAny(text, "重", "严重", "胸痛", "呼吸困难", "昏迷") ? "较重" : "中等";
  }

  private boolean containsAny(String value, String... needles) {
    for (String needle : needles) {
      if (value.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private AgentPlaneRun createAgentRun(
      CaseView careCase,
      String agentName,
      String artifactKind,
      String body,
      String trainability) {
    AgentPlaneArtifact artifact = agentPlaneClient.createArtifact(new CreateAgentPlaneArtifactRequest(
        TENANT_ID,
        artifactKind,
        "text/plain",
        trainability,
        body,
        Map.of("caseId", careCase.caseId(), "deidentified", String.valueOf(!"REVIEW_REQUIRED".equals(trainability)))));
    return agentPlaneClient.createRun(new CreateAgentPlaneRunRequest(
        careCase.agentSessionId(),
        agentName,
        artifact.artifactId(),
        "carecopilot-api"));
  }

  private CreateAgentPlaneEventRequest eventRequest(
      long sequence,
      String eventType,
      String message,
      String toolName,
      String artifactRef,
      String trainability) {
    return new CreateAgentPlaneEventRequest(
        sequence,
        eventType,
        RUNTIME_PROVIDER,
        message,
        toolName,
        toolName == null ? null : "tool-call-" + UUID.randomUUID(),
        artifactRef,
        0,
        null,
        trainability);
  }

  private AgentPlaneTask submitAgentTask(CaseView careCase, AgentPlaneRun run, String agentName) {
    return submitAgentTask(careCase, run, agentName, Map.of());
  }

  private AgentPlaneTask submitAgentTask(
      CaseView careCase,
      AgentPlaneRun run,
      String agentName,
      Map<String, Object> additionalPayload) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sessionId", careCase.agentSessionId());
    payload.put("runId", run.runId());
    payload.put("runtimeProvider", RUNTIME_PROVIDER);
    payload.put("agentName", agentName);
    payload.put("caseId", careCase.caseId());
    payload.putAll(additionalPayload);
    return agentPlaneClient.submitAgentTask(new SubmitAgentTaskRequest(
        "AGENT_TASK",
        9,
        1,
        1024,
        payload));
  }

  private SafetyView symptomSafety(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    List<String> reasons = new ArrayList<>();
    if (normalized.contains("胸痛")) {
      reasons.add("胸痛");
    }
    if (normalized.contains("呼吸困难")) {
      reasons.add("呼吸困难");
    }
    if (normalized.contains("意识") || normalized.contains("昏迷")) {
      reasons.add("意识改变");
    }
    if (!reasons.isEmpty()) {
      return new SafetyView(true, "URGENT_CARE", reasons);
    }
    if (containsMedicationChangeRequest(normalized)) {
      return new SafetyView(false, "MEDICATION_BOUNDARY", List.of("用药调整请求"));
    }
    return new SafetyView(false, "ALLOW", List.of());
  }

  private boolean containsMedicationChangeRequest(String normalized) {
    return normalized.contains("停药")
        || normalized.contains("停掉")
        || normalized.contains("加药")
        || normalized.contains("加量")
        || normalized.contains("减药")
        || normalized.contains("减量")
        || normalized.contains("换药")
        || normalized.contains("改药");
  }

  private StructuredCarePlanView structuredCarePlan(SafetyView safety) {
    if (safety.redFlag()) {
      return new StructuredCarePlanView(
          "URGENT",
          List.of("尽快线下急诊或呼叫急救", "避免自行用药或等待观察", "携带既往病历和当前用药清单"),
          List.of("症状何时开始？", "是否伴随出汗、呼吸困难或意识改变？", "是否有心脑血管病史或近期外伤？"),
          List.of("NOT_A_DIAGNOSIS", "EMERGENCY_FIRST"));
    }
    if ("MEDICATION_BOUNDARY".equals(safety.decision())) {
      return new StructuredCarePlanView(
          "MEDICATION_REVIEW",
          List.of("记录药名、剂量和不适反应", "联系开药医生或药师确认", "若出现严重过敏或呼吸困难立即急诊"),
          List.of("药物名称和剂量是多少？", "不适从什么时候开始？", "是否自行漏服或调整过剂量？"),
          List.of("MEDICATION_CHANGE_REQUIRES_CLINICIAN", "NOT_A_PRESCRIPTION"));
    }
    return new StructuredCarePlanView(
        "ROUTINE",
        List.of("整理症状发生时间线", "记录诱因、缓解因素和伴随症状", "按需预约线下就诊"),
        List.of("症状持续多久？", "严重程度是否变化？", "近期是否有新药、感染或生活方式变化？"),
        List.of("NOT_A_DIAGNOSIS"));
  }

  private EvalCheckView check(String name, boolean passed, String message) {
    return new EvalCheckView(name, passed, message);
  }

  private List<String> abnormalItems(String reportText) {
    List<String> items = new ArrayList<>();
    for (String token : reportText.split("[；;]")) {
      if (token.contains("↑") || token.contains("↓")) {
        items.add(token.trim());
      }
    }
    return items;
  }

  private List<ReportFindingView> reportFindings(String reportText) {
    List<ReportFindingView> findings = new ArrayList<>();
    for (String token : reportText.split("[；;]")) {
      String trimmed = token.trim();
      if (trimmed.contains("↑") || trimmed.contains("↓")) {
        String direction = trimmed.contains("↓") ? "LOW" : "HIGH";
        String cleaned = trimmed.replace("↑", "").replace("↓", "").trim();
        String[] parts = cleaned.split("\\s+", 2);
        String name = parts.length == 0 ? cleaned : parts[0];
        String value = parts.length > 1 ? parts[1] : "";
        String explanation = "HIGH".equals(direction)
            ? "该指标高于常见参考范围，需结合症状和医生判断。"
            : "该指标低于常见参考范围，需结合症状和医生判断。";
        findings.add(new ReportFindingView(name, value, direction, explanation));
      }
    }
    return findings;
  }

  private List<String> visitPreparationDocuments() {
    return List.of("近期检查报告和影像资料", "当前用药清单和过敏史", "症状发生时间线和既往诊疗记录");
  }

  private void appendTimeline(
      CaseView careCase,
      String eventType,
      String title,
      String summary,
      String agentRunId,
      String agentTaskJobId) {
    timelines.computeIfAbsent(careCase.caseId(), ignored -> new ArrayList<>()).add(new TimelineEntryView(
        "timeline-" + UUID.randomUUID(),
        careCase.caseId(),
        eventType,
        title,
        summary,
        agentRunId,
        agentTaskJobId,
        Instant.now(clock)));
  }

  private void appendAudit(
      CaseView careCase,
      String eventType,
      String summary,
      String agentRunId,
      String agentTaskJobId,
      String trainability) {
    auditEvents.add(new CareAuditEventView(
        "audit-" + UUID.randomUUID(),
        careCase.caseId(),
        eventType,
        "carecopilot-api",
        summary,
        agentRunId,
        agentTaskJobId,
        trainability,
        Instant.now(clock)));
  }

  private void restoreState(CareCopilotState state) {
    cases.clear();
    timelines.clear();
    auditEvents.clear();
    if (state == null) {
      return;
    }
    cases.putAll(state.cases());
    state.timelines().forEach((caseId, entries) -> timelines.put(caseId, new ArrayList<>(entries)));
    auditEvents.addAll(state.auditEvents());
  }

  private void persistState() {
    stateStore.save(new CareCopilotState(
        new LinkedHashMap<>(cases),
        copyTimelines(),
        List.copyOf(auditEvents)));
  }

  private Map<String, List<TimelineEntryView>> copyTimelines() {
    Map<String, List<TimelineEntryView>> copy = new LinkedHashMap<>();
    timelines.forEach((caseId, entries) -> copy.put(caseId, List.copyOf(entries)));
    return copy;
  }

  private String toJson(CareDatasetRecordView record) {
    return toJsonObject(record);
  }

  private String toJsonObject(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize CareCopilot payload", exception);
    }
  }

  private CaseView requireCase(String caseId) {
    CaseView careCase = cases.get(caseId);
    if (careCase == null) {
      throw new IllegalArgumentException("Unknown case: " + caseId);
    }
    return careCase;
  }
}
