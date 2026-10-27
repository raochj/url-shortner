package com.schwab.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.dto.response.OrchestrationRunResponse;
import com.schwab.urlshortener.entity.ApprovalRequest;
import com.schwab.urlshortener.entity.OrchestratorRun;
import com.schwab.urlshortener.orchestration.scenarios.AmbiguousScenario;
import com.schwab.urlshortener.orchestration.scenarios.BrownfieldScenario;
import com.schwab.urlshortener.orchestration.scenarios.GreenfieldScenario;
import com.schwab.urlshortener.repository.OrchestratorRunRepository;
import com.schwab.urlshortener.repository.OrchestratorTaskLogRepository;
import com.schwab.urlshortener.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentOrchestrator unit tests")
class AgentOrchestratorTest {
  @Mock private OrchestratorEngine engine;
  @Mock private GreenfieldScenario greenfieldScenario;
  @Mock private BrownfieldScenario brownfieldScenario;
  @Mock private AmbiguousScenario ambiguousScenario;
  @Mock private OrchestratorRunRepository runRepository;
  @Mock private OrchestratorTaskLogRepository taskLogRepository;
  @Mock private ApprovalCoordinator approvalCoordinator;
  @Mock private ReliabilityTracker reliabilityTracker;
  @Mock private AuditTrailService auditTrailService;
  @Mock private UserRepository userRepository;
  @InjectMocks private AgentOrchestrator agentOrchestrator;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // Inject ObjectMapper (InjectMocks doesn't inject final fields easily)
  @org.junit.jupiter.api.BeforeEach
  void injectObjectMapper() throws Exception {
    var field = AgentOrchestrator.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(agentOrchestrator, objectMapper);
  }

  // =========================================================================
  @Test
  @DisplayName("startRun_greenfieldScenario_createsRunAndReturnsId")
  void startRun_greenfieldScenario_createsRunAndReturnsId() {
    // Arrange
    UUID runId = UUID.randomUUID();
    OrchestratorRun savedRun =
        OrchestratorRun.builder()
            .id(runId)
            .scenarioType("GREENFIELD")
            .status("PENDING")
            .parameters("{}")
            .build();
    TaskGraph mockGraph = new TaskGraph("gf-" + runId, "GREENFIELD");
    when(engine.getActiveGraphs()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    when(runRepository.save(any(OrchestratorRun.class))).thenReturn(savedRun);
    when(greenfieldScenario.buildGraph(any())).thenReturn(mockGraph);
    // Act
    String returnedId = agentOrchestrator.startRun("GREENFIELD", Map.of(), null);
    // Assert
    assertThat(returnedId).isEqualTo(runId.toString());
    ArgumentCaptor<OrchestratorRun> runCaptor = ArgumentCaptor.forClass(OrchestratorRun.class);
    verify(runRepository).save(runCaptor.capture());
    assertThat(runCaptor.getValue().getScenarioType()).isEqualTo("GREENFIELD");
    verify(engine).startExecution(eq(runId.toString()), eq(mockGraph));
  }

  @Test
  @DisplayName("submitApproval_pendingTask_updatesApprovalRequest")
  void submitApproval_pendingTask_updatesApprovalRequest() {
    // Arrange
    String runId = UUID.randomUUID().toString();
    String taskId = "DESIGN-001";
    UUID approvalId = UUID.randomUUID();
    ApprovalRequest pendingRequest =
        ApprovalRequest.builder()
            .id(approvalId)
            .runId(UUID.fromString(runId))
            .taskId(taskId)
            .status("PENDING")
            .build();
    when(approvalCoordinator.getPendingApproval(UUID.fromString(runId), taskId))
        .thenReturn(Optional.of(pendingRequest));
    when(approvalCoordinator.processDecision(eq(approvalId), eq(true), eq(null), anyString()))
        .thenReturn(ApprovalRequest.builder().id(approvalId).status("APPROVED").build());
    // Act — should not throw
    agentOrchestrator.submitApproval(runId, taskId, true, null, "LGTM");
    // Assert
    verify(approvalCoordinator).processDecision(approvalId, true, null, "LGTM");
  }

  @Test
  @DisplayName("submitApproval_noPendingApproval_throwsIllegalState")
  void submitApproval_noPendingApproval_throwsIllegalState() {
    String runId = UUID.randomUUID().toString();
    when(approvalCoordinator.getPendingApproval(any(), anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> agentOrchestrator.submitApproval(runId, "REL-001", true, null, "Approved"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No pending approval");
  }

  @Test
  @DisplayName("getRunState_completedRun_returnsCorrectMetrics")
  void getRunState_completedRun_returnsCorrectMetrics() throws Exception {
    // Arrange
    UUID runId = UUID.randomUUID();
    OrchestratorRun completedRun =
        OrchestratorRun.builder()
            .id(runId)
            .scenarioType("GREENFIELD")
            .status("COMPLETED")
            .parameters("{}")
            .build();
    when(engine.getActiveGraphs()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    // Stub snapshot-based graph (no active graph in engine)
    when(runRepository.findById(runId)).thenReturn(Optional.of(completedRun));
    when(taskLogRepository.findAllByRunIdOrderByStartedAt(runId)).thenReturn(List.of());
    // Act
    OrchestrationRunResponse state = agentOrchestrator.getRunState(runId.toString());
    // Assert
    assertThat(state).isNotNull();
    assertThat(state.runId()).isEqualTo(runId.toString());
    assertThat(state.status()).isEqualTo("COMPLETED");
    assertThat(state.scenarioType()).isEqualTo("GREENFIELD");
  }
}
