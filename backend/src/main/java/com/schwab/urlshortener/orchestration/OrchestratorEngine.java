package com.schwab.urlshortener.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.entity.OrchestratorRun;
import com.schwab.urlshortener.entity.OrchestratorTaskLog;
import com.schwab.urlshortener.orchestration.model.ApprovalGate;
import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
import com.schwab.urlshortener.repository.OrchestratorRunRepository;
import com.schwab.urlshortener.repository.OrchestratorTaskLogRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core DAG execution engine. Drives task execution via a 500ms tick loop.
 *
 * <p>Execution lifecycle per task: 1. Policy guardrail evaluation 2. Approval gate check
 * (AWAITING_APPROVAL ↔ APPROVED) 3. Stage entry gate check 4. IN_PROGRESS → executor call →
 * COMPLETED | retry → FAILED → rollback
 */
@Slf4j
@Service
public class OrchestratorEngine {
  private final ScheduledExecutorService ticker =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "orch-ticker"));
  private final Executor taskPool;
  private final ReliabilityTracker reliabilityTracker;
  private final ApprovalCoordinator approvalCoordinator;
  private final OrchestratorRunRepository runRepository;
  private final OrchestratorTaskLogRepository taskLogRepository;
  private final ObjectMapper objectMapper;
  // In-flight state
  private final ConcurrentHashMap<String, TaskGraph> activeGraphs = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> runIdToScenario = new ConcurrentHashMap<>();
  // Track which nodes are currently being submitted to avoid double-submission
  private final Set<String> submittedKeys = ConcurrentHashMap.newKeySet();
  private ScheduledFuture<?> tickFuture;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "These collaborators are container-managed services with intentional shared state.")
  public OrchestratorEngine(
      @Qualifier("orchestratorExecutor") Executor taskPool,
      ReliabilityTracker reliabilityTracker,
      ApprovalCoordinator approvalCoordinator,
      OrchestratorRunRepository runRepository,
      OrchestratorTaskLogRepository taskLogRepository,
      ObjectMapper objectMapper) {
    this.taskPool = taskPool;
    this.reliabilityTracker = reliabilityTracker;
    this.approvalCoordinator = approvalCoordinator;
    this.runRepository = runRepository;
    this.taskLogRepository = taskLogRepository;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void startTicker() {
    tickFuture = ticker.scheduleAtFixedRate(this::tick, 500, 500, TimeUnit.MILLISECONDS);
    log.info("OrchestratorEngine ticker started (500ms interval)");
  }

  @PreDestroy
  public void stopTicker() {
    if (tickFuture != null) tickFuture.cancel(false);
    ticker.shutdown();
    log.info("OrchestratorEngine ticker stopped");
  }

  // =========================================================================
  // Public API
  // =========================================================================
  public Map<String, TaskGraph> getActiveGraphs() {
    return Map.copyOf(activeGraphs);
  }

  /** Registers a graph for execution and marks the DB run as RUNNING. */
  @Transactional
  public void startExecution(String runId, TaskGraph graph) {
    activeGraphs.put(runId, graph);
    runRepository
        .findById(UUID.fromString(runId))
        .ifPresent(
            run -> {
              run.setStatus("RUNNING");
              run.setStartedAt(OffsetDateTime.now());
              runRepository.save(run);
            });
    log.info(
        "OrchestratorEngine: startExecution runId={} nodes={}", runId, graph.getNodes().size());
  }

  // =========================================================================
  // Tick loop
  // =========================================================================
  private void tick() {
    try {
      for (String runId : activeGraphs.keySet()) {
        processRun(runId);
      }
    } catch (Exception e) {
      log.error("OrchestratorEngine tick error", e);
    }
  }

  private void processRun(String runId) {
    TaskGraph graph = activeGraphs.get(runId);
    if (graph == null) return;
    Set<String> completed = graph.getCompletedNodeIds();
    // Find and submit ready nodes
    List<TaskNode> readyNodes = graph.getReadyNodes(completed);
    for (TaskNode node : readyNodes) {
      String key = runId + ":" + node.getId();
      if (submittedKeys.add(key)) {
        submitTask(runId, node, key);
      }
    }
    // Re-check awaiting-approval nodes (approval may have been granted externally)
    for (TaskNode node : graph.getNodes().values()) {
      if (node.getStatus() == TaskStatus.AWAITING_APPROVAL) {
        String key = runId + ":" + node.getId() + ":approval-check";
        if (submittedKeys.add(key)) {
          try {
            taskPool.execute(
                () -> {
                  try {
                    checkApprovalAndResume(runId, node);
                  } finally {
                    submittedKeys.remove(key);
                  }
                });
          } catch (RejectedExecutionException ex) {
            submittedKeys.remove(key);
            log.warn("Approval check deferred because the worker queue is full: {}", key);
          }
        }
      }
    }
    // Finalize if complete
    if (graph.isComplete()) {
      finalizeRun(runId, graph);
    }
  }

  // =========================================================================
  // Node execution
  // =========================================================================
  private void executeNode(String runId, TaskNode node) {
    String nodeKey = runId + ":" + node.getId();
    boolean retryScheduled = false;
    try {
      log.info(
          "executeNode: runId={} nodeId={} attempt={}",
          runId,
          node.getId(),
          node.getAttemptCount() + 1);
      // 1. Stage entry gate
      TaskGraph graph = activeGraphs.get(runId);
      if (graph != null && !checkStageEntryGate(node.getStage(), graph)) {
        log.debug("Stage entry gate not met for node {} stage {}", node.getId(), node.getStage());
        submittedKeys.remove(nodeKey);
        return;
      }
      // 2. Policy guardrail evaluation
      Map<String, Object> context = buildContext(graph);
      List<PolicyViolation> violations = new ArrayList<>();
      for (PolicyGuardrail guardrail : node.getGuardrails()) {
        violations.addAll(guardrail.evaluate(node, context));
      }
      if (!violations.isEmpty()) {
        PolicyViolation blocking =
            violations.stream()
                .filter(v -> v.severity() == PolicyViolation.Severity.BLOCKING)
                .findFirst()
                .orElse(null);
        if (blocking != null) {
          log.warn(
              "Blocking policy violation on node {}: {}", node.getId(), blocking.description());
          node.setBlockingPolicyViolation(blocking.ruleId() + ": " + blocking.description());
          node.transitionTo(TaskStatus.FAILED);
          saveTaskLog(runId, node, null, violations);
          triggerRollback(runId, node, graph);
          safeStopRemainingNodes(graph, node.getId());
          return;
        }
        // Non-blocking: log warnings and continue
        violations.stream()
            .filter(v -> v.severity() == PolicyViolation.Severity.WARNING)
            .forEach(w -> log.warn("Policy warning on node {}: {}", node.getId(), w.description()));
      }
      // 3. Approval gate
      ApprovalGate gate = node.getApprovalGate();
      if (gate.required() && !gate.canAutoApprove() && node.getStatus() != TaskStatus.APPROVED) {
        // Transition to AWAITING_APPROVAL and create approval request
        node.transitionTo(TaskStatus.AWAITING_APPROVAL);
        approvalCoordinator.createApproval(runId, node.getId(), gate.approvalPrompt());
        log.info("Node {} is AWAITING_APPROVAL", node.getId());
        submittedKeys.remove(nodeKey);
        return;
      }
      // 4. Execute
      int attempt = node.incrementAttempt();
      node.transitionTo(TaskStatus.IN_PROGRESS);
      Instant start = Instant.now();
      reliabilityTracker.recordStart(runId, node.getId());
      try {
        TaskResult result = node.getExecutor().call();
        // Wrap result with real start/end times
        Instant end = Instant.now();
        TaskResult timed =
            new TaskResult(
                node.getId(),
                true,
                result.message(),
                result.artifacts(),
                start,
                end,
                attempt,
                null);
        node.setLastResult(timed);
        node.transitionTo(TaskStatus.COMPLETED);
        reliabilityTracker.recordComplete(runId, node.getId());
        if (attempt > 1) reliabilityTracker.recordRecovery(runId, node.getId());
        saveTaskLog(runId, node, timed, violations);
        log.info(
            "Node {} COMPLETED in {}ms (attempt {})", node.getId(), timed.latencyMillis(), attempt);
      } catch (Exception ex) {
        Instant end = Instant.now();
        log.error("Node {} failed on attempt {}: {}", node.getId(), attempt, ex.getMessage());
        reliabilityTracker.recordFailure(runId, node.getId());
        RetryPolicy retryPolicy = node.getRetryPolicy();
        if (attempt < retryPolicy.maxAttempts()) {
          // Schedule retry with exponential backoff
          long delayMs = retryPolicy.getDelayForAttempt(attempt).toMillis();
          log.info(
              "Scheduling retry for node {} in {}ms (attempt {}/{})",
              node.getId(),
              delayMs,
              attempt,
              retryPolicy.maxAttempts());
          node.transitionTo(TaskStatus.PENDING);
          retryScheduled = true;
          ticker.schedule(
              () -> {
                submittedKeys.remove(nodeKey);
                TaskGraph activeGraph = activeGraphs.get(runId);
                if (activeGraph != null
                    && node.getStatus() == TaskStatus.PENDING
                    && submittedKeys.add(nodeKey)) {
                  submitTask(runId, node, nodeKey);
                }
              },
              delayMs,
              TimeUnit.MILLISECONDS);
        } else {
          TaskResult failResult =
              TaskResult.failure(node.getId(), ex.getMessage(), start, end, attempt);
          node.setLastResult(failResult);
          node.transitionTo(TaskStatus.FAILED);
          saveTaskLog(runId, node, failResult, violations);
          triggerRollback(runId, node, graph);
          safeStopRemainingNodes(graph, node.getId());
        }
      }
    } finally {
      if (!retryScheduled) {
        submittedKeys.remove(nodeKey);
      }
    }
  }

  private void checkApprovalAndResume(String runId, TaskNode node) {
    UUID runUuid = UUID.fromString(runId);
    // Check if expired
    approvalCoordinator
        .getPendingApproval(runUuid, node.getId())
        .ifPresent(
            req -> {
              if (approvalCoordinator.isExpired(req)) {
                log.warn("Approval expired for node {} in run {}", node.getId(), runId);
                approvalCoordinator.expire(req);
                node.transitionTo(TaskStatus.FAILED);
                node.setBlockingPolicyViolation("Approval request expired");
                TaskGraph graph = activeGraphs.get(runId);
                saveTaskLog(runId, node, null, List.of());
                triggerRollback(runId, node, graph);
                safeStopRemainingNodes(graph, node.getId());
              }
            });
    if (node.getStatus() == TaskStatus.FAILED) return;
    if (approvalCoordinator.isRejected(runUuid, node.getId())) {
      log.warn("Approval rejected for node {} in run {}", node.getId(), runId);
      node.transitionTo(TaskStatus.FAILED);
      node.setBlockingPolicyViolation("Approval request rejected");
      TaskGraph graph = activeGraphs.get(runId);
      saveTaskLog(runId, node, null, List.of());
      triggerRollback(runId, node, graph);
      safeStopRemainingNodes(graph, node.getId());
      return;
    }
    if (approvalCoordinator.isApproved(runUuid, node.getId())) {
      log.info(
          "Approval granted for node {} in run {} — transitioning to APPROVED",
          node.getId(),
          runId);
      node.transitionTo(TaskStatus.APPROVED);
      // The next tick discovers APPROVED nodes through the normal bounded submission path.
    }
  }

  private void submitTask(String runId, TaskNode node, String key) {
    try {
      taskPool.execute(() -> executeNode(runId, node));
    } catch (RejectedExecutionException ex) {
      submittedKeys.remove(key);
      log.warn("Task submission deferred because the worker queue is full: {}", key);
    }
  }

  // =========================================================================
  // Stage entry gate
  // =========================================================================
  private boolean checkStageEntryGate(TaskStage stage, TaskGraph graph) {
    TaskStage[] stageOrder = TaskStage.values();
    int currentOrdinal = stage.ordinal();
    for (int i = 0; i < currentOrdinal; i++) {
      TaskStage previousStage = stageOrder[i];
      List<TaskNode> prevNodes = graph.getNodesByStage(previousStage);
      for (TaskNode prevNode : prevNodes) {
        TaskStatus s = prevNode.getStatus();
        if (s != TaskStatus.COMPLETED && s != TaskStatus.SKIPPED && s != TaskStatus.ROLLED_BACK) {
          return false;
        }
      }
    }
    return true;
  }

  // =========================================================================
  // Rollback
  // =========================================================================
  private void triggerRollback(String runId, TaskNode failedNode, TaskGraph graph) {
    if (graph == null) return;
    log.info("Triggering rollback chain for runId={} failedNode={}", runId, failedNode.getId());
    // Walk in reverse topological order (simple reverse iteration)
    List<TaskNode> allNodes = new ArrayList<>(graph.getNodes().values());
    for (int i = allNodes.size() - 1; i >= 0; i--) {
      TaskNode node = allNodes.get(i);
      if (node.getStatus() == TaskStatus.COMPLETED && node.getRollbackHandler() != null) {
        try {
          log.info("Rolling back node {}", node.getId());
          boolean rolled = node.getRollbackHandler().rollback(node.getLastResult());
          if (rolled) {
            node.transitionTo(TaskStatus.ROLLED_BACK);
            reliabilityTracker.recordRecovery(runId, node.getId());
          }
        } catch (Exception e) {
          log.error("Rollback failed for node {}: {}", node.getId(), e.getMessage());
        }
      }
    }
  }

  private void safeStopRemainingNodes(TaskGraph graph, String failedNodeId) {
    if (graph == null) return;
    for (TaskNode node : graph.getNodes().values()) {
      TaskStatus status = node.getStatus();
      if (!node.getId().equals(failedNodeId)
          && (status == TaskStatus.PENDING
              || status == TaskStatus.AWAITING_APPROVAL
              || status == TaskStatus.APPROVED)) {
        node.transitionTo(TaskStatus.SKIPPED);
      }
    }
  }

  // =========================================================================
  // Finalization
  // =========================================================================
  private void finalizeRun(String runId, TaskGraph graph) {
    if (!activeGraphs.containsKey(runId)) return;
    boolean anyFailed = graph.hasAnyFailed();
    String finalStatus = anyFailed ? "FAILED" : "COMPLETED";
    try {
      String snapshot = objectMapper.writeValueAsString(graph.serialize());
      String metricsSnapshot =
          objectMapper.writeValueAsString(reliabilityTracker.snapshot(runId, graph));
      OrchestratorRun run =
          runRepository
              .findById(UUID.fromString(runId))
              .orElseThrow(() -> new IllegalStateException("Run disappeared: " + runId));
      run.setStatus(finalStatus);
      run.setCompletedAt(OffsetDateTime.now());
      run.setDagSnapshot(snapshot);
      run.setMetricsSnapshot(metricsSnapshot);
      runRepository.save(run);
      activeGraphs.remove(runId, graph);
      reliabilityTracker.removeRun(runId);
      log.info("Run {} finalized with status={}", runId, finalStatus);
    } catch (RuntimeException | JsonProcessingException e) {
      log.error("Failed to durably finalize run {}; it will be retried", runId, e);
    }
  }

  // =========================================================================
  // Task log persistence
  // =========================================================================
  @Transactional
  public void saveTaskLog(
      String runId, TaskNode node, TaskResult result, List<PolicyViolation> policyChecks) {
    try {
      String resultPayload =
          result != null && !result.artifacts().isEmpty()
              ? objectMapper.writeValueAsString(result.artifacts())
              : "{}";
      String policyJson = objectMapper.writeValueAsString(policyChecks);
      OrchestratorTaskLog log_entry =
          OrchestratorTaskLog.builder()
              .runId(UUID.fromString(runId))
              .taskId(node.getId())
              .taskName(node.getName())
              .stage(node.getStage().name())
              .status(node.getStatus().name())
              .attemptNumber(node.getAttemptCount())
              .startedAt(
                  result != null
                      ? OffsetDateTime.ofInstant(result.startTime(), java.time.ZoneOffset.UTC)
                      : OffsetDateTime.now())
              .completedAt(
                  result != null
                      ? OffsetDateTime.ofInstant(result.endTime(), java.time.ZoneOffset.UTC)
                      : OffsetDateTime.now())
              .latencyMs(result != null ? result.latencyMillis() : null)
              .resultPayload(resultPayload)
              .errorDetail(
                  result != null ? result.errorDetail() : node.getBlockingPolicyViolation())
              .policyChecks(policyJson)
              .build();
      taskLogRepository.save(log_entry);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize task log for node {} in run {}", node.getId(), runId, e);
    }
  }

  // =========================================================================
  // Context building
  // =========================================================================
  private Map<String, Object> buildContext(TaskGraph graph) {
    if (graph == null) return Map.of();
    // Collect artifacts from all completed nodes into a flat context map
    Map<String, Object> context = new java.util.HashMap<>();
    for (TaskNode node : graph.getNodes().values()) {
      if (node.getStatus() == TaskStatus.COMPLETED && node.getLastResult() != null) {
        context.putAll(node.getLastResult().artifacts());
        // Mark testing as completed if any TESTING node completed
        if (node.getStage() == TaskStage.TESTING) {
          context.put("testing-completed", true);
        }
      }
    }
    return context;
  }
}
