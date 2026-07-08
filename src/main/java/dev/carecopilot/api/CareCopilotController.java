package dev.carecopilot.api;

import dev.carecopilot.domain.CareDatasetExportRequest;
import dev.carecopilot.domain.CaseView;
import dev.carecopilot.domain.CaseAuditEventsView;
import dev.carecopilot.domain.CaseTimelineView;
import dev.carecopilot.domain.ChatConfirmRequest;
import dev.carecopilot.domain.ChatContextResponse;
import dev.carecopilot.domain.ChatIntentRequest;
import dev.carecopilot.domain.ChatIntentResponse;
import dev.carecopilot.domain.ClearChatContextRequest;
import dev.carecopilot.domain.CreateCaseRequest;
import dev.carecopilot.domain.EvalResultView;
import dev.carecopilot.domain.ReportExplanationRequest;
import dev.carecopilot.domain.SymptomIntakeRequest;
import dev.carecopilot.domain.VisitPreparationRequest;
import dev.carecopilot.domain.WorkflowResponse;
import dev.carecopilot.service.CareCopilotService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CareCopilotController {
  private final CareCopilotService service;

  public CareCopilotController(CareCopilotService service) {
    this.service = service;
  }

  @PostMapping("/cases")
  public CaseView createCase(@Valid @RequestBody CreateCaseRequest request) {
    return service.createCase(request);
  }

  @GetMapping("/cases/{caseId}")
  public CaseView getCase(@PathVariable String caseId) {
    return service.caseById(caseId);
  }

  @GetMapping("/cases/{caseId}/timeline")
  public CaseTimelineView caseTimeline(@PathVariable String caseId) {
    return service.caseTimeline(caseId);
  }

  @GetMapping("/cases/{caseId}/audit-events")
  public CaseAuditEventsView auditEvents(@PathVariable String caseId) {
    return service.auditEvents(caseId);
  }

  @PostMapping(value = "/datasets/export/jsonl", produces = MediaType.TEXT_PLAIN_VALUE)
  public String exportDatasetJsonl(@Valid @RequestBody CareDatasetExportRequest request) {
    return service.exportDatasetJsonl(request);
  }

  @PostMapping("/workflows/symptom-intake")
  public WorkflowResponse symptomIntake(@Valid @RequestBody SymptomIntakeRequest request) {
    return service.symptomIntake(request);
  }

  @PostMapping("/chat/intent")
  public ChatIntentResponse detectChatIntent(@Valid @RequestBody ChatIntentRequest request) {
    return service.detectChatIntent(request);
  }

  @PostMapping("/chat/confirm")
  public WorkflowResponse confirmChatIntent(@Valid @RequestBody ChatConfirmRequest request) {
    return service.confirmChatIntent(request);
  }

  @PostMapping("/chat/context/clear")
  public ChatContextResponse clearChatContext(@Valid @RequestBody ClearChatContextRequest request) {
    return service.clearChatContext(request);
  }

  @PostMapping("/workflows/report-explanation")
  public WorkflowResponse reportExplanation(@Valid @RequestBody ReportExplanationRequest request) {
    return service.reportExplanation(request);
  }

  @PostMapping("/workflows/visit-preparation")
  public WorkflowResponse visitPreparation(@Valid @RequestBody VisitPreparationRequest request) {
    return service.visitPreparation(request);
  }

  @GetMapping("/evals/carecopilot/mvp")
  public EvalResultView evaluateMvp() {
    return service.evaluateMvp();
  }
}
