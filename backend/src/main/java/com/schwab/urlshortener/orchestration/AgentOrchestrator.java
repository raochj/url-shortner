package com.schwab.urlshortener.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.dto.response.OrchestrationRunResponse;
import com.schwab.urlshortener.dto.response.OrchestrationRunSummaryResponse;
import com.schwab.urlshortener.dto.response.ReliabilityMetricsResponse;
import com.schwab.urlshortener.dto.response.TaskNodeResponse;
import com.schwab.urlshortener.entity.OrchestratorRun;
import com.schwab.urlshortener.entity.OrchestratorTaskLog;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.exception.CapacityExceededException;
import com.schwab.urlshortener.orchestration.model.ReliabilityMetrics;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
import com.schwab.urlshortener.orchestration.scenarios.AmbiguousScenario;
import com.schwab.urlshortener.orchestration.scenarios.BrownfieldScenario;
import com.schwab.urlshortener.orchestration.scenarios.GreenfieldScenario;
import com.schwab.urlshortener.repository.OrchestratorRunRepository;
import com.schwab.urlshortener.repository.OrchestratorTaskLogRepository;
import com.schwab.urlshortener.repository.UserRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public API facade for the orchestration subsystem.
 *
 * <p>Responsibilities: - Create and start runs for the three scenario types - Forward approval
 * decisions to ApprovalCoordinator - Provide run state, metrics, DAG visualization, and
 * SSE-friendly snapshots - Handle dynamic task injection for the AMBIGUOUS scenario
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {
  private final OrchestratorEngine engine;
  private final GreenfieldScenario greenfieldScenario;
  private final BrownfieldScenario brownfieldScenario;
  private final AmbiguousScenario ambiguousScenario;
  private final OrchestratorRunRepository runRepository;
  private final OrchestratorTaskLogRepository taskLogRepository;
  private final ApprovalCoordinator approvalCoordinator;
  private final ReliabilityTracker reliabilityTracker;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Value("${app.orchestration.max-active-runs:100}")
  private int maxActiveRuns = 100;

  // Track which AMBIGUOUS runs have had tasks injected to avoid double-injection.
  // Non-final so Lombok does not include it in the @RequiredArgsConstructor generated constructor.
  private Set<String> ambiguousInjected = ConcurrentHashMap.newKeySet();

  @Scheduled(fixedDelayString = "${app.orchestration.dynamic-replan-interval-ms:250}")
  public void processDynamicReplanning() {
    ambiguousInjected.removeIf(runId -> !engine.getActiveGraphs().containsKey(runId));
    for (Map.Entry<String, TaskGraph> entry : engine.getActiveGraphs().entrySet()) {
      TaskGraph graph = entry.getValue();
      if ("AMBIGUOUS".equalsIgnoreCase(graph.getScenarioType())) {
        maybeInjectAmbiguousTasks(entry.getKey(), graph);
      }
    }
  }

  // =========================================================================
  // Start a run
  // =========================================================================
  @Transactional
  public String startRun(String scenarioType, Map<String, Object> parameters, Long userId) {
    if (engine.getActiveGraphs().size() >= maxActiveRuns) {
      throw new CapacityExceededException(
          "Orchestration capacity is temporarily exhausted; retry later");
    }
    log.info("AgentOrchestrator.startRun scenarioType={} userId={}", scenarioType, userId);
    User initiatedBy = userId != null ? userRepository.findById(userId).orElse(null) : null;
    String paramsJson;
    try {
      paramsJson = objectMapper.writeValueAsString(parameters != null ? parameters : Map.of());
    } catch (Exception e) {
      paramsJson = "{}";
    }
    OrchestratorRun run =
        OrchestratorRun.builder()
            .scenarioType(scenarioType.toUpperCase(Locale.ROOT))
            .status("PENDING")
            .parameters(paramsJson)
            .initiatedBy(initiatedBy)
            .build();
    run = runRepository.save(run);
    String runId = run.getId().toString();
    TaskGraph graph = buildGraphForScenario(scenarioType, parameters);
    graph.validate();
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              engine.startExecution(runId, graph);
            }
          });
    } else {
      engine.startExecution(runId, graph);
    }
    log.info("Run created: runId={} scenario={}", runId, scenarioType);
    return runId;
  }

  @Transactional
  public String startRunForUser(
      String scenarioType, Map<String, Object> parameters, String username) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    return startRun(scenarioType, parameters, user.getId());
  }

  private TaskGraph buildGraphForScenario(String scenarioType, Map<String, Object> parameters) {
    return switch (scenarioType.toUpperCase(Locale.ROOT)) {
      case "GREENFIELD" ->
          greenfieldScenario.buildGraph(parameters != null ? parameters : Map.of());
      case "BROWNFIELD" ->
          brownfieldScenario.buildGraph(parameters != null ? parameters : Map.of());
      case "AMBIGUOUS" -> ambiguousScenario.buildGraph(parameters != null ? parameters : Map.of());
      default -> throw new IllegalArgumentException("Unknown scenario type: " + scenarioType);
    };
  }

  // =========================================================================
  // Approvals
  // =========================================================================
  @Transactional
  public void submitApproval(
      String runId, String taskId, boolean approved, Long approverId, String comment) {
    UUID runUuid = UUID.fromString(runId);
    approvalCoordinator
        .getPendingApproval(runUuid, taskId)
        .ifPresentOrElse(
            req -> approvalCoordinator.processDecision(req.getId(), approved, approverId, comment),
            () -> {
              throw new IllegalStateException(
                  "No pending approval for run=" + runId + " task=" + taskId);
            });
  }

  @Transactional
  public void submitApprovalForUser(
      String runId, String taskId, boolean approved, String approverUsername, String comment) {
    User approver =
        userRepository
            .findByUsername(approverUsername)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + approverUsername));
    submitApproval(runId, taskId, approved, approver.getId(), comment);
  }

  // =========================================================================
  // Run state
  // =========================================================================
  @Transactional(readOnly = true)
  public OrchestrationRunResponse getRunState(String runId) {
    UUID runUuid = UUID.fromString(runId);
    OrchestratorRun run =
        runRepository
            .findById(runUuid)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    // Try to get live graph first
    TaskGraph graph = engine.getActiveGraphs().get(runId);
    // Build task responses
    List<TaskNodeResponse> taskResponses;
    if (graph != null) {
      taskResponses = buildTaskResponsesFromGraph(graph);
    } else {
      // Fall back to task logs
      taskResponses =
          buildTaskResponsesFromLogs(taskLogRepository.findAllByRunIdOrderByStartedAt(runUuid));
    }

    // Deserialize parameters
    Map<String, Object> params = Map.of();
    if (run.getParameters() != null && !run.getParameters().isBlank()) {
      try {
        params = objectMapper.readValue(run.getParameters(), new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        log.debug("Could not deserialize run parameters for {}", runId);
      }
    }
    ReliabilityMetricsResponse metricsResp = null;
    if (graph != null) {
      ReliabilityMetrics m = reliabilityTracker.snapshot(runId, graph);
      metricsResp = toMetricsResponse(m);
    } else if (run.getMetricsSnapshot() != null) {
      try {
        metricsResp =
            toMetricsResponse(
                objectMapper.readValue(run.getMetricsSnapshot(), ReliabilityMetrics.class));
      } catch (JsonProcessingException e) {
        log.warn("Could not deserialize metrics snapshot for run={}", runId);
      }
    }
    // Handle AMBIGUOUS injection: if CLARIFY-001 is completed and tasks not yet injected
    if ("AMBIGUOUS".equalsIgnoreCase(run.getScenarioType()) && graph != null) {
      maybeInjectAmbiguousTasks(runId, graph);
    }
    return new OrchestrationRunResponse(
        runId,
        run.getScenarioType(),
        run.getStatus(),
        taskResponses,
        params,
        run.getStartedAt(),
        run.getCompletedAt(),
        metricsResp);
  }

  @Transactional(readOnly = true)
  public OrchestrationRunResponse getRunState(String runId, String username, boolean admin) {
    requireRunAccess(runId, username, admin);
    return getRunState(runId);
  }

  // =========================================================================
  // Metrics
  // =========================================================================
  public ReliabilityMetrics getMetrics(String runId) {
    TaskGraph graph = engine.getActiveGraphs().get(runId);
    if (graph != null) return reliabilityTracker.snapshot(runId, graph);
    return runRepository
        .findById(UUID.fromString(runId))
        .map(OrchestratorRun::getMetricsSnapshot)
        .filter(snapshot -> snapshot != null && !snapshot.isBlank())
        .map(
            snapshot -> {
              try {
                return objectMapper.readValue(snapshot, ReliabilityMetrics.class);
              } catch (JsonProcessingException e) {
                throw new IllegalStateException("Invalid stored metrics for run=" + runId, e);
              }
            })
        .orElse(new ReliabilityMetrics(runId, 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0, 0, 0, 0, 0));
  }

  @Transactional(readOnly = true)
  public ReliabilityMetrics getMetrics(String runId, String username, boolean admin) {
    requireRunAccess(runId, username, admin);
    return getMetrics(runId);
  }

  // =========================================================================
  // Task injection
  // =========================================================================
  public void injectTask(String runId, TaskNode newNode, List<String> afterIds) {
    TaskGraph graph = engine.getActiveGraphs().get(runId);
    if (graph == null) {
      throw new IllegalStateException("Run " + runId + " is not active — cannot inject tasks");
    }
    graph.injectNode(newNode, afterIds);
    log.info("Injected task {} into run {} after {}", newNode.getId(), runId, afterIds);
  }

  // =========================================================================
  // DAG visualization
  // =========================================================================
  public Map<String, Object> getDagVisualization(String runId) {
    TaskGraph graph = engine.getActiveGraphs().get(runId);
    if (graph != null) {
      return graph.getDagVisualization();
    }

    // Try to reconstruct from snapshot
    graph = reconstructGraphFromSnapshot(runId);
    return graph != null
        ? graph.getDagVisualization()
        : Map.of("nodes", List.of(), "edges", List.of());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDagVisualization(String runId, String username, boolean admin) {
    requireRunAccess(runId, username, admin);
    return getDagVisualization(runId);
  }

  // =========================================================================
  // List runs
  // =========================================================================
  @Transactional(readOnly = true)
  public Page<OrchestrationRunSummaryResponse> listRuns(Pageable pageable) {
    return runRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toRunSummary);
  }

  @Transactional(readOnly = true)
  public Page<OrchestrationRunSummaryResponse> listRuns(
      Pageable pageable, String username, boolean admin) {
    if (admin) return listRuns(pageable);
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    return runRepository
        .findAllByInitiatedByIdOrderByCreatedAtDesc(user.getId(), pageable)
        .map(this::toRunSummary);
  }

  @Transactional(readOnly = true)
  public List<OrchestratorTaskLog> getTaskLogs(String runId) {
    return taskLogRepository.findAllByRunIdOrderByStartedAt(UUID.fromString(runId));
  }

  @Transactional(readOnly = true)
  public List<OrchestratorTaskLog> getTaskLogs(String runId, String username, boolean admin) {
    requireRunAccess(runId, username, admin);
    return getTaskLogs(runId);
  }

  // =========================================================================
  // Scenario catalogue
  // =========================================================================
  public List<Map<String, Object>> listScenarios() {
    List<Map<String, Object>> scenarios = new ArrayList<>();
    scenarios.add(
        Map.of(
            "type",
            "GREENFIELD",
            "title",
            "New URL Shortener (Greenfield)",
            "description",
            "Build the Schwab URL Shortener from scratch with 12 tasks spanning requirements,"
                + " design, implementation, testing, documentation, and release.",
            "taskCount",
            12,
            "stages",
            List.of(
                "REQUIREMENTS", "DESIGN", "IMPLEMENTATION", "TESTING", "DOCUMENTATION", "RELEASE"),
            "features",
            List.of(
                "Parallel implementation",
                "Security guardrails",
                "Human approval gates",
                "Release validation"),
            "parameters",
            Map.of("none required", "All parameters have defaults")));
    scenarios.add(
        Map.of(
            "type",
            "BROWNFIELD",
            "title",
            "Add QR Code Generation (Brownfield)",
            "description",
            "Extend the existing URL shortener with QR code generation using ZXing. Demonstrates"
                + " retry behavior, security guardrails, and blue-green deployment.",
            "taskCount",
            9,
            "stages",
            List.of(
                "REQUIREMENTS", "DESIGN", "IMPLEMENTATION", "TESTING", "DOCUMENTATION", "RELEASE"),
            "approvalGates",
            List.of("REL-B-001 (blue-green deploy)"),
            "features",
            List.of(
                "Bounded retry", "Rollback propagation", "Impact analysis", "Blue-green release")));
    scenarios.add(
        Map.of(
            "type",
            "AMBIGUOUS",
            "title",
            "Performance Optimization (Ambiguous)",
            "description",
            "Dynamic graph that starts with a single clarification task, then injects 4"
                + " optimization tasks based on the hypothesis produced by CLARIFY-001.",
            "taskCount",
            0,
            "stages",
            List.of("REQUIREMENTS", "IMPLEMENTATION", "TESTING", "DOCUMENTATION"),
            "approvalGates",
            List.of("None — fully automated after clarification"),
            "parameters",
            Map.of(
                "goal",
                "Optional performance goal string, e.g. 'Improve P95 redirect latency to under"
                    + " 50ms'"),
            "features",
            List.of(
                "Requirement clarification",
                "Dynamic re-planning",
                "Parallel optimization",
                "Benchmark validation")));
    return scenarios;
  }

  // =========================================================================
  // AMBIGUOUS scenario auto-injection
  // =========================================================================
  @SuppressFBWarnings(
      value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
      justification = "Injection must propagate failures after clearing its idempotency marker.")
  private void maybeInjectAmbiguousTasks(String runId, TaskGraph graph) {
    if (ambiguousInjected.contains(runId)) return;
    TaskNode clarifyNode = graph.getNodes().get("CLARIFY-001");
    if (clarifyNode != null
        && clarifyNode.getStatus() == TaskStatus.COMPLETED
        && clarifyNode.getLastResult() != null
        && ambiguousInjected.add(runId)) {
      try {
        Map<String, Object> artifacts = clarifyNode.getLastResult().artifacts();
        log.info(
            "AMBIGUOUS: CLARIFY-001 completed for run {}. Injecting follow-on tasks...", runId);
        List<TaskNode> injectionTasks = ambiguousScenario.getInjectionTasks(artifacts);
        for (TaskNode task : injectionTasks) {
          graph.injectNode(task, List.of("CLARIFY-001"));
        }
        log.info("AMBIGUOUS: Injected {} tasks into run {}", injectionTasks.size(), runId);
        graph.setDynamicExpansionPending(false);
      } catch (RuntimeException ex) {
        ambiguousInjected.remove(runId);
        throw ex;
      }
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================
  private List<TaskNodeResponse> buildTaskResponsesFromGraph(TaskGraph graph) {
    List<TaskNodeResponse> responses = new ArrayList<>();
    for (TaskNode node : graph.getNodes().values()) {
      TaskResult result = node.getLastResult();
      responses.add(
          new TaskNodeResponse(
              node.getId(),
              node.getName(),
              node.getDescription(),
              node.getStage().name(),
              node.getStatus().name(),
              node.getAttemptCount(),
              result != null ? result.latencyMillis() : null,
              node.getDependencyIds(),
              result != null ? result.errorDetail() : node.getBlockingPolicyViolation(),
              node.getApprovalGate().required(),
              node.getApprovalGate().approvalPrompt()));
    }
    return responses;
  }

  private List<TaskNodeResponse> buildTaskResponsesFromLogs(List<OrchestratorTaskLog> logs) {
    List<TaskNodeResponse> responses = new ArrayList<>();
    for (OrchestratorTaskLog log_entry : logs) {
      responses.add(
          new TaskNodeResponse(
              log_entry.getTaskId(),
              log_entry.getTaskName(),
              "",
              log_entry.getStage(),
              log_entry.getStatus(),
              log_entry.getAttemptNumber(),
              log_entry.getLatencyMs(),
              List.of(),
              log_entry.getErrorDetail(),
              false,
              ""));
    }
    return responses;
  }

  private ReliabilityMetricsResponse toMetricsResponse(ReliabilityMetrics m) {
    return new ReliabilityMetricsResponse(
        m.runId(),
        m.totalTasks(),
        m.completedTasks(),
        m.failedTasks(),
        m.rolledBackTasks(),
        m.retryCount(),
        m.successRate(),
        m.rollbackRate(),
        m.totalElapsedMs(),
        m.meanTimeToRecoverMs(),
        m.p50LatencyMs(),
        m.p95LatencyMs(),
        m.approvalsPending(),
        m.approvalsGranted());
  }

  private OrchestrationRunSummaryResponse toRunSummary(OrchestratorRun run) {
    return new OrchestrationRunSummaryResponse(
        run.getId().toString(),
        run.getScenarioType(),
        run.getStatus(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getCreatedAt(),
        run.getInitiatedBy() != null ? run.getInitiatedBy().getUsername() : null);
  }

  private void requireRunAccess(String runId, String username, boolean admin) {
    if (admin) return;
    OrchestratorRun run =
        runRepository
            .findById(UUID.fromString(runId))
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    if (run.getInitiatedBy() == null || !run.getInitiatedBy().getUsername().equals(username)) {
      throw new AccessDeniedException("You do not have access to orchestration run: " + runId);
    }
  }

  /**
   * Attempts to reconstruct a read-only TaskGraph from a stored DAG snapshot. Returns null if no
   * snapshot is available.
   */
  @SuppressWarnings("unchecked")
  private TaskGraph reconstructGraphFromSnapshot(String runId) {
    return runRepository
        .findById(UUID.fromString(runId))
        .filter(run -> run.getDagSnapshot() != null)
        .map(
            run -> {
              try {
                Map<String, Object> snapshot =
                    objectMapper.readValue(run.getDagSnapshot(), new TypeReference<>() {});
                String graphId = (String) snapshot.getOrDefault("graphId", runId);
                String scenarioType =
                    (String) snapshot.getOrDefault("scenarioType", run.getScenarioType());
                TaskGraph graph = new TaskGraph(graphId, scenarioType);
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
                if (nodes != null) {
                  for (Map<String, Object> n : nodes) {
                    String nodeId = (String) n.get("id");
                    String name = (String) n.get("name");
                    String stageStr = (String) n.get("stage");
                    String statusStr = (String) n.getOrDefault("status", "COMPLETED");
                    var stage =
                        com.schwab.urlshortener.orchestration.model.TaskStage.valueOf(stageStr);
                    var status = TaskStatus.valueOf(statusStr);
                    @SuppressWarnings("unchecked")
                    List<String> deps = (List<String>) n.getOrDefault("dependencies", List.of());
                    TaskNode node =
                        TaskNode.builder()
                            .id(nodeId)
                            .name(name)
                            .stage(stage)
                            .dependsOn(deps)
                            .executor(
                                () -> {
                                  throw new UnsupportedOperationException(
                                      "Snapshot node not executable");
                                })
                            .build();
                    node.transitionTo(status);
                    graph.addNode(node);
                  }
                }
                return graph;
              } catch (RuntimeException | JsonProcessingException e) {
                log.error("Failed to reconstruct graph from snapshot for run {}", runId, e);
                return null;
              }
            })
        .orElse(null);
  }
}
