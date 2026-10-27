package com.schwab.urlshortener.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.entity.OrchestratorRun;
import com.schwab.urlshortener.entity.OrchestratorTaskLog;
import com.schwab.urlshortener.orchestration.model.ApprovalGate;
import com.schwab.urlshortener.orchestration.model.ExecutionGate;
import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.RunControlState;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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
  private final ExecutorService actionExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final Executor taskPool;
  private final ReliabilityTracker reliabilityTracker;
  private final ApprovalCoordinator approvalCoordinator;
  private final OrchestratorRunRepository runRepository;
  private final OrchestratorTaskLogRepository taskLogRepository;
  private final ObjectMapper objectMapper;
  private final AuditTrailService auditTrailService;
  // In-flight state
  private final ConcurrentHashMap<String, TaskGraph> activeGraphs = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> runIdToScenario = new ConcurrentHashMap<>();
  // Track which nodes are currently being submitted to avoid double-submission
  private final Set<String> submittedKeys = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, RunControlState> controlStates = new ConcurrentHashMap<>();
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
      ObjectMapper objectMapper,
      AuditTrailService auditTrailService) {
    this.taskPool = taskPool;
    this.reliabilityTracker = reliabilityTracker;
    this.approvalCoordinator = approvalCoordinator;
    this.runRepository = runRepository;
    this.taskLogRepository = taskLogRepository;
    this.objectMapper = objectMapper;
    this.auditTrailService = auditTrailService;
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
    actionExecutor.shutdownNow();
    log.info("OrchestratorEngine ticker stopped");
  }

  // =========================================================================
  // Public API
  // =========================================================================
  public Map<String, TaskGraph> getActiveGraphs() {
    return Map.copyOf(activeGraphs);
  }

  /** Registers a graph for execution and marks the DB run as RUNNING. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void startExecution(String runId, TaskGraph graph) {
    activeGraphs.put(runId, graph);
    controlStates.put(runId, RunControlState.RUNNING);
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
    auditTrailService.record(
        runId,
        null,
        "RUN_STARTED",
        "system",
        graph.getContextVersion(),
        Map.of("graphId", graph.getGraphId(), "scenario", graph.getScenarioType()));
    checkpoint(runId, graph);
  }

  public void pauseRun(String runId, String actor, String reason) {
    TaskGraph graph = requireActiveGraph(runId);
    controlStates.put(runId, RunControlState.PAUSED);
    updateRunStatus(runId, "PAUSED");
    auditTrailService.record(
        runId, null, "RUN_PAUSED", actor, graph.getContextVersion(), Map.of("reason", reason));
    checkpoint(runId, graph);
  }

  public void resumeRun(String runId, String actor, String reason) {
    TaskGraph graph = requireActiveGraph(runId);
    if (controlStates.get(runId) != RunControlState.PAUSED) {
      throw new IllegalStateException("Run is not paused: " + runId);
    }
    controlStates.put(runId, RunControlState.RUNNING);
    updateRunStatus(runId, "RUNNING");
    auditTrailService.record(
        runId, null, "RUN_RESUMED", actor, graph.getContextVersion(), Map.of("reason", reason));
  }

  public void requestSafeStop(String runId, String actor, String reason) {
    TaskGraph graph = requireActiveGraph(runId);
    controlStates.put(runId, RunControlState.SAFE_STOPPING);
    updateRunStatus(runId, "SAFE_STOPPING");
    safeStopRemainingNodes(graph, "");
    approvalCoordinator.supersedeApprovals(
        UUID.fromString(runId),
        graph.getNodes().values().stream()
            .filter(node -> node.getStatus() == TaskStatus.SKIPPED)
            .map(TaskNode::getId)
            .collect(java.util.stream.Collectors.toSet()));
    auditTrailService.record(
        runId,
        null,
        "SAFE_STOP_REQUESTED",
        actor,
        graph.getContextVersion(),
        Map.of("reason", reason));
    checkpoint(runId, graph);
  }

  public Set<String> replan(
      String runId, String changedTaskId, Map<String, Object> revisedArtifacts, String actor,
      String reason) {
    TaskGraph graph = requireActiveGraph(runId);
    if (controlStates.get(runId) != RunControlState.PAUSED) {
      throw new IllegalStateException("Run must be paused before re-planning");
    }
    if (graph.getNodes().values().stream()
        .anyMatch(node -> node.getStatus() == TaskStatus.IN_PROGRESS)) {
      throw new IllegalStateException("Re-plan must wait until all in-flight tasks reach a boundary");
    }
    TaskNode source = graph.getNodes().get(changedTaskId);
    if (source == null || source.getLastResult() == null) {
      throw new IllegalArgumentException("Changed task has no completed output: " + changedTaskId);
    }
    Set<String> invalidated = graph.invalidateDescendants(changedTaskId);
    reliabilityTracker.recordReplan(runId);
    TaskResult previous = source.getLastResult();
    source.setLastResult(
        new TaskResult(
            source.getId(), true, "Output revised by governed re-plan", revisedArtifacts,
            previous.startTime(), Instant.now(), previous.attemptNumber(), null));
    int approvalsRevoked =
        approvalCoordinator.supersedeApprovals(UUID.fromString(runId), invalidated);
    auditTrailService.record(
        runId,
        changedTaskId,
        "PLAN_REVISED",
        actor,
        graph.getContextVersion(),
        Map.of(
            "reason", reason,
            "invalidatedTasks", invalidated.stream().sorted().toList(),
            "approvalsRevoked", approvalsRevoked,
            "revisedArtifacts", revisedArtifacts));
    checkpoint(runId, graph);
    return invalidated;
  }

  public void graphChanged(
      String runId, String eventType, String actor, Map<String, ?> payload) {
    TaskGraph graph = requireActiveGraph(runId);
    auditTrailService.record(
        runId, null, eventType, actor, graph.getContextVersion(), payload);
    checkpoint(runId, graph);
  }

  public void recordApprovalDecision(
      String runId, String taskId, boolean approved, String actor, String comment) {
    TaskGraph graph = requireActiveGraph(runId);
    auditTrailService.record(
        runId,
        taskId,
        approved ? "APPROVAL_DECIDED_APPROVED" : "APPROVAL_DECIDED_REJECTED",
        actor,
        graph.getContextVersion(),
        Map.of("comment", comment == null ? "" : comment));
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

  void processRun(String runId) {
    TaskGraph graph = activeGraphs.get(runId);
    if (graph == null) return;
    RunControlState controlState = controlStates.getOrDefault(runId, RunControlState.RUNNING);
    if (controlState == RunControlState.PAUSED) return;
    if (controlState == RunControlState.SAFE_STOPPING) {
      boolean hasRunning =
          graph.getNodes().values().stream()
              .anyMatch(node -> node.getStatus() == TaskStatus.IN_PROGRESS);
      if (!hasRunning) {
        triggerRollback(runId, null, graph);
        finalizeRun(runId, graph, "CANCELLED");
      }
      return;
    }
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
      RunControlState controlState =
          controlStates.getOrDefault(runId, RunControlState.SAFE_STOPPING);
      TaskStatus currentStatus = node.getStatus();
      if (controlState != RunControlState.RUNNING
          || (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.APPROVED)) {
        log.debug(
            "Deferring node {} because run control={} and task status={}",
            node.getId(),
            controlState,
            currentStatus);
        return;
      }
      log.info(
          "executeNode: runId={} nodeId={} attempt={}",
          runId,
          node.getId(),
          node.getAttemptCount() + 1);
      // 1. Explicit entry gates. Cross-stage parallelism is controlled by graph dependencies,
      // rather than a global stage barrier that would serialize independent paths.
      TaskGraph graph = activeGraphs.get(runId);
      Map<String, Object> context = buildContext(graph);
      List<PolicyViolation> violations = new ArrayList<>();
      violations.addAll(evaluateGates("ENTRY", node.getEntryGates(), context));
      // 2. Policy guardrail evaluation
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
          auditTrailService.record(
              runId, node.getId(), "POLICY_BLOCKED", "system", graph.getContextVersion(),
              Map.of("rule", blocking.ruleId(), "description", blocking.description()));
          triggerRollback(runId, node, graph);
          safeStopRemainingNodes(graph, node.getId());
          checkpoint(runId, graph);
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
        auditTrailService.record(
            runId, node.getId(), "APPROVAL_REQUESTED", "system", graph.getContextVersion(),
            Map.of("prompt", gate.approvalPrompt(), "roles", gate.requiredApproverRoles()));
        checkpoint(runId, graph);
        log.info("Node {} is AWAITING_APPROVAL", node.getId());
        submittedKeys.remove(nodeKey);
        return;
      }
      // 4. Execute
      int attempt = node.incrementAttempt();
      node.transitionTo(TaskStatus.IN_PROGRESS);
      Instant start = Instant.now();
      reliabilityTracker.recordStart(runId, node.getId());
      auditTrailService.record(
          runId, node.getId(), "TASK_STARTED", "system", graph.getContextVersion(),
          Map.of("attempt", attempt));
      try {
        TaskResult result = invokeWithTimeout(node.getExecutor(), node.getExecutionTimeout());
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
        List<PolicyViolation> exitViolations =
            evaluateGates("EXIT", node.getExitGates(), timed.artifacts());
        violations.addAll(exitViolations);
        PolicyViolation blockedExit =
            exitViolations.stream()
                .filter(v -> v.severity() == PolicyViolation.Severity.BLOCKING)
                .findFirst()
                .orElse(null);
        if (blockedExit != null) throw new IllegalStateException(blockedExit.description());
        node.setLastResult(timed);
        if (controlStates.get(runId) == RunControlState.SAFE_STOPPING) {
          if (node.getRollbackHandler() != null && node.getRollbackHandler().rollback(timed)) {
            node.transitionTo(TaskStatus.ROLLED_BACK);
          } else {
            node.transitionTo(TaskStatus.COMPLETED);
          }
        } else {
          node.transitionTo(TaskStatus.COMPLETED);
        }
        reliabilityTracker.recordComplete(runId, node.getId());
        if (attempt > 1) reliabilityTracker.recordRecovery(runId, node.getId());
        saveTaskLog(runId, node, timed, violations);
        auditTrailService.record(
            runId, node.getId(), "TASK_COMPLETED", "system", graph.getContextVersion(),
            Map.of("attempt", attempt, "artifacts", timed.artifacts()));
        checkpoint(runId, graph);
        log.info(
            "Node {} COMPLETED in {}ms (attempt {})", node.getId(), timed.latencyMillis(), attempt);
      } catch (InterruptedException
          | java.util.concurrent.ExecutionException
          | TimeoutException
          | RuntimeException ex) {
        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
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
          TaskResult attemptFailure =
              TaskResult.failure(node.getId(), safeMessage(ex), start, end, attempt);
          saveTaskLog(runId, node, attemptFailure, violations);
          auditTrailService.record(
              runId, node.getId(), "TASK_RETRY_SCHEDULED", "system", graph.getContextVersion(),
              Map.of("attempt", attempt, "delayMs", delayMs, "error", safeMessage(ex)));
          checkpoint(runId, graph);
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
          if (node.getFallbackExecutor() != null) {
            executeFallback(runId, node, graph, violations, start, attempt, ex);
          } else {
            TaskResult failResult =
                TaskResult.failure(node.getId(), safeMessage(ex), start, end, attempt);
            node.setLastResult(failResult);
            node.transitionTo(TaskStatus.FAILED);
            saveTaskLog(runId, node, failResult, violations);
            auditTrailService.record(
                runId, node.getId(), "TASK_FAILED", "system", graph.getContextVersion(),
                Map.of("attempt", attempt, "error", safeMessage(ex)));
            triggerRollback(runId, node, graph);
            safeStopRemainingNodes(graph, node.getId());
            checkpoint(runId, graph);
          }
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
                auditTrailService.record(
                    runId, node.getId(), "APPROVAL_EXPIRED", "system",
                    graph.getContextVersion(), Map.of());
                triggerRollback(runId, node, graph);
                safeStopRemainingNodes(graph, node.getId());
                checkpoint(runId, graph);
              }
            });
    if (node.getStatus() == TaskStatus.FAILED) return;
    if (approvalCoordinator.isRejected(runUuid, node.getId())) {
      log.warn("Approval rejected for node {} in run {}", node.getId(), runId);
      node.transitionTo(TaskStatus.FAILED);
      node.setBlockingPolicyViolation("Approval request rejected");
      TaskGraph graph = activeGraphs.get(runId);
      saveTaskLog(runId, node, null, List.of());
      auditTrailService.record(
          runId, node.getId(), "APPROVAL_REJECTED", "system", graph.getContextVersion(), Map.of());
      triggerRollback(runId, node, graph);
      safeStopRemainingNodes(graph, node.getId());
      checkpoint(runId, graph);
      return;
    }
    if (approvalCoordinator.isApproved(runUuid, node.getId())) {
      log.info(
          "Approval granted for node {} in run {} — transitioning to APPROVED",
          node.getId(),
          runId);
      node.transitionTo(TaskStatus.APPROVED);
      TaskGraph graph = activeGraphs.get(runId);
      if (graph != null) {
        auditTrailService.record(
            runId, node.getId(), "APPROVAL_GRANTED", "human", graph.getContextVersion(), Map.of());
        checkpoint(runId, graph);
      }
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
  // Rollback
  // =========================================================================
  private void triggerRollback(String runId, TaskNode failedNode, TaskGraph graph) {
    if (graph == null) return;
    log.info(
        "Triggering rollback chain for runId={} failedNode={}",
        runId,
        failedNode != null ? failedNode.getId() : "safe-stop");
    // Compensation is deterministic and reverses dependency order.
    List<TaskNode> allNodes = new ArrayList<>(graph.topologicalOrder());
    for (int i = allNodes.size() - 1; i >= 0; i--) {
      TaskNode node = allNodes.get(i);
      if (node.getStatus() == TaskStatus.COMPLETED && node.getRollbackHandler() != null) {
        try {
          log.info("Rolling back node {}", node.getId());
          boolean rolled = node.getRollbackHandler().rollback(node.getLastResult());
          if (rolled) {
            node.transitionTo(TaskStatus.ROLLED_BACK);
            reliabilityTracker.recordRecovery(runId, node.getId());
            auditTrailService.record(
                runId, node.getId(), "TASK_ROLLED_BACK", "system", graph.getContextVersion(),
                Map.of("trigger", failedNode != null ? failedNode.getId() : "safe-stop"));
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
    finalizeRun(runId, graph, finalStatus);
  }

  private void finalizeRun(String runId, TaskGraph graph, String finalStatus) {
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
      auditTrailService.record(
          runId, null, "RUN_FINALIZED", "system", graph.getContextVersion(),
          Map.of("status", finalStatus));
      activeGraphs.remove(runId, graph);
      controlStates.remove(runId);
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
    context.put("context-version", graph.getContextVersion());
    Map<String, Object> lineage = new java.util.LinkedHashMap<>();
    for (TaskNode node : graph.getNodes().values()) {
      if ((node.getStatus() == TaskStatus.COMPLETED
              || node.getStatus() == TaskStatus.FALLBACK_COMPLETED)
          && node.getLastResult() != null) {
        context.putAll(node.getLastResult().artifacts());
        context.put("task." + node.getId() + ".artifacts", node.getLastResult().artifacts());
        lineage.put(
            node.getId(),
            Map.of(
                "dependencies", node.getDependencyIds(),
                "attempt", node.getLastResult().attemptNumber(),
                "artifacts", node.getLastResult().artifacts()));
        // Mark testing as completed if any TESTING node completed
        if (node.getStage() == TaskStage.TESTING) {
          context.put("testing-completed", true);
        }
      }
    }
    context.put("decision-lineage", lineage);
    return context;
  }

  private List<PolicyViolation> evaluateGates(
      String phase, List<ExecutionGate> gates, Map<String, Object> evidence) {
    List<PolicyViolation> violations = new ArrayList<>();
    for (ExecutionGate gate : gates) {
      List<String> missing = gate.missingFrom(evidence);
      if (!missing.isEmpty()) {
        violations.add(
            new PolicyViolation(
                "ExecutionGate",
                phase + "-" + gate.id(),
                phase + " gate '" + gate.description() + "' missing evidence: " + missing,
                PolicyViolation.Severity.BLOCKING));
      }
    }
    return violations;
  }

  private void executeFallback(
      String runId,
      TaskNode node,
      TaskGraph graph,
      List<PolicyViolation> violations,
      Instant start,
      int attempt,
      Exception primaryFailure) {
    try {
      TaskResult fallback =
          invokeWithTimeout(node.getFallbackExecutor(), node.getExecutionTimeout());
      TaskResult timed =
          new TaskResult(
              node.getId(), true, fallback.message(), fallback.artifacts(), start, Instant.now(),
              attempt, "Primary action failed: " + safeMessage(primaryFailure));
      node.setLastResult(timed);
      node.transitionTo(TaskStatus.FALLBACK_COMPLETED);
      reliabilityTracker.recordComplete(runId, node.getId());
      reliabilityTracker.recordFallback(runId, node.getId());
      reliabilityTracker.recordRecovery(runId, node.getId());
      saveTaskLog(runId, node, timed, violations);
      auditTrailService.record(
          runId, node.getId(), "FALLBACK_COMPLETED", "system", graph.getContextVersion(),
          Map.of("primaryError", safeMessage(primaryFailure), "artifacts", timed.artifacts()));
      checkpoint(runId, graph);
    } catch (InterruptedException
        | java.util.concurrent.ExecutionException
        | TimeoutException
        | RuntimeException fallbackFailure) {
      if (fallbackFailure instanceof InterruptedException) Thread.currentThread().interrupt();
      TaskResult failed =
          TaskResult.failure(node.getId(), safeMessage(fallbackFailure), start, Instant.now(), attempt);
      node.setLastResult(failed);
      node.transitionTo(TaskStatus.FAILED);
      saveTaskLog(runId, node, failed, violations);
      auditTrailService.record(
          runId, node.getId(), "FALLBACK_FAILED", "system", graph.getContextVersion(),
          Map.of("error", safeMessage(fallbackFailure)));
      triggerRollback(runId, node, graph);
      safeStopRemainingNodes(graph, node.getId());
      checkpoint(runId, graph);
    }
  }

  private void checkpoint(String runId, TaskGraph graph) {
    // Parallel branches complete on different worker threads. Serialize their snapshot writes on the
    // run's graph so two workers cannot race the OrchestratorRun optimistic-lock version and turn a
    // successful task into a false retry. A multi-instance deployment must additionally use a
    // distributed run-owner/lease; this engine deliberately supports one active owner per run.
    synchronized (graph) {
      try {
        OrchestratorRun run =
            runRepository.findById(UUID.fromString(runId)).orElseThrow();
        run.setDagSnapshot(objectMapper.writeValueAsString(graph.serialize()));
        run.setMetricsSnapshot(
            objectMapper.writeValueAsString(reliabilityTracker.snapshot(runId, graph)));
        runRepository.save(run);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Unable to checkpoint orchestration state", e);
      }
    }
  }

  private TaskGraph requireActiveGraph(String runId) {
    TaskGraph graph = activeGraphs.get(runId);
    if (graph == null) throw new IllegalStateException("Run is not active: " + runId);
    return graph;
  }

  private void updateRunStatus(String runId, String status) {
    OrchestratorRun run =
        runRepository.findById(UUID.fromString(runId))
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    run.setStatus(status);
    runRepository.save(run);
  }

  private String safeMessage(Exception exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  private <T> T invokeWithTimeout(java.util.concurrent.Callable<T> action, java.time.Duration timeout)
      throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
    Future<T> future = actionExecutor.submit(action);
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new TimeoutException("Task exceeded execution timeout of " + timeout.toMillis() + "ms");
    }
  }
}
