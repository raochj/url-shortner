package com.schwab.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.dto.request.ApprovalDecisionRequest;
import com.schwab.urlshortener.dto.request.ReplanRequest;
import com.schwab.urlshortener.dto.request.RunControlRequest;
import com.schwab.urlshortener.dto.request.StartOrchestrationRequest;
import com.schwab.urlshortener.dto.response.ApiResponse;
import com.schwab.urlshortener.dto.response.OrchestrationRunResponse;
import com.schwab.urlshortener.dto.response.OrchestrationRunSummaryResponse;
import com.schwab.urlshortener.dto.response.ReliabilityMetricsResponse;
import com.schwab.urlshortener.entity.OrchestratorTaskLog;
import com.schwab.urlshortener.entity.OrchestrationAuditEvent;
import com.schwab.urlshortener.orchestration.AgentOrchestrator;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.model.ReliabilityMetrics;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/v1/orchestration")
@RequiredArgsConstructor
public class OrchestrationController {
  private final AgentOrchestrator agentOrchestrator;
  private final ObjectMapper objectMapper;
  // Dedicated thread pool for SSE emitters
  private final ScheduledExecutorService sseScheduler =
      Executors.newScheduledThreadPool(4, r -> new Thread(r, "orch-sse"));

  // =========================================================================
  // Scenario catalogue
  // =========================================================================
  @GetMapping("/scenarios")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listScenarios() {
    return ResponseEntity.ok(ApiResponse.ok(agentOrchestrator.listScenarios()));
  }

  // =========================================================================
  // Start a run
  // =========================================================================
  @PostMapping("/runs")
  public ResponseEntity<ApiResponse<Map<String, String>>> startRun(
      @Valid @RequestBody StartOrchestrationRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    String runId =
        agentOrchestrator.startRunForUser(
            request.scenarioType(), request.parameters(), userDetails.getUsername());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(Map.of("runId", runId), "Orchestration run started"));
  }

  // =========================================================================
  // List runs
  // =========================================================================
  @GetMapping("/runs")
  public ResponseEntity<ApiResponse<Page<OrchestrationRunSummaryResponse>>> listRuns(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @AuthenticationPrincipal UserDetails userDetails) {
    Page<OrchestrationRunSummaryResponse> runs =
        agentOrchestrator.listRuns(
            PageRequest.of(page, size, Sort.by("createdAt").descending()),
            userDetails.getUsername(),
            isAdmin(userDetails));
    return ResponseEntity.ok(ApiResponse.ok(runs));
  }

  // =========================================================================
  // Get run state
  // =========================================================================
  @GetMapping("/runs/{runId}")
  public ResponseEntity<ApiResponse<OrchestrationRunResponse>> getRunState(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    OrchestrationRunResponse state =
        agentOrchestrator.getRunState(runId, userDetails.getUsername(), isAdmin(userDetails));
    return ResponseEntity.ok(ApiResponse.ok(state));
  }

  // =========================================================================
  // DAG visualization
  // =========================================================================
  @GetMapping("/runs/{runId}/dag")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getDagVisualization(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    Map<String, Object> dag =
        agentOrchestrator.getDagVisualization(
            runId, userDetails.getUsername(), isAdmin(userDetails));
    return ResponseEntity.ok(ApiResponse.ok(dag));
  }

  // =========================================================================
  // Metrics
  // =========================================================================
  @GetMapping("/runs/{runId}/metrics")
  public ResponseEntity<ApiResponse<ReliabilityMetricsResponse>> getMetrics(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    ReliabilityMetrics m =
        agentOrchestrator.getMetrics(runId, userDetails.getUsername(), isAdmin(userDetails));
    ReliabilityMetricsResponse resp =
        new ReliabilityMetricsResponse(
            m.runId(),
            m.totalTasks(),
            m.completedTasks(),
            m.failedTasks(),
            m.rolledBackTasks(),
            m.retryCount(),
            m.fallbackCount(),
            m.replanCount(),
            m.successRate(),
            m.retryRate(),
            m.rollbackRate(),
            m.totalElapsedMs(),
            m.meanTimeToRecoverMs(),
            m.p50LatencyMs(),
            m.p95LatencyMs(),
            m.approvalsPending(),
            m.approvalsGranted());
    return ResponseEntity.ok(ApiResponse.ok(resp));
  }

  // =========================================================================
  // Task logs
  // =========================================================================
  @GetMapping("/runs/{runId}/logs")
  public ResponseEntity<ApiResponse<List<OrchestratorTaskLog>>> getTaskLogs(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    List<OrchestratorTaskLog> logs =
        agentOrchestrator.getTaskLogs(runId, userDetails.getUsername(), isAdmin(userDetails));
    return ResponseEntity.ok(ApiResponse.ok(logs));
  }

  @GetMapping("/runs/{runId}/audit-events")
  public ResponseEntity<ApiResponse<List<OrchestrationAuditEvent>>> getAuditEvents(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    return ResponseEntity.ok(
        ApiResponse.ok(
            agentOrchestrator.getAuditEvents(
                runId, userDetails.getUsername(), isAdmin(userDetails))));
  }

  @GetMapping("/runs/{runId}/audit-integrity")
  public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyAuditIntegrity(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    boolean valid =
        agentOrchestrator.verifyAuditIntegrity(
            runId, userDetails.getUsername(), isAdmin(userDetails));
    return ResponseEntity.ok(ApiResponse.ok(Map.of("valid", valid)));
  }

  @PostMapping("/runs/{runId}/pause")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> pauseRun(
      @PathVariable String runId,
      @Valid @RequestBody RunControlRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    agentOrchestrator.pauseRun(runId, userDetails.getUsername(), true, request.reason());
    return ResponseEntity.ok(ApiResponse.ok(null, "Run paused at the next safe boundary"));
  }

  @PostMapping("/runs/{runId}/resume")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> resumeRun(
      @PathVariable String runId,
      @Valid @RequestBody RunControlRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    agentOrchestrator.resumeRun(runId, userDetails.getUsername(), true, request.reason());
    return ResponseEntity.ok(ApiResponse.ok(null, "Run resumed"));
  }

  @PostMapping("/runs/{runId}/safe-stop")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> safeStopRun(
      @PathVariable String runId,
      @Valid @RequestBody RunControlRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    agentOrchestrator.safeStopRun(runId, userDetails.getUsername(), true, request.reason());
    return ResponseEntity.accepted()
        .body(ApiResponse.ok(null, "Safe stop requested; in-flight work will reach a boundary"));
  }

  @PostMapping("/runs/{runId}/replan")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> replanRun(
      @PathVariable String runId,
      @Valid @RequestBody ReplanRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Set<String> invalidated =
        agentOrchestrator.replanRun(
            runId,
            request.changedTaskId(),
            request.revisedArtifacts(),
            userDetails.getUsername(),
            true,
            request.reason());
    return ResponseEntity.ok(
        ApiResponse.ok(
            Map.of("contextVersionChanged", true, "invalidatedTasks", invalidated),
            "Governed re-plan applied; resume the run after review"));
  }

  // =========================================================================
  // Approval submission (ADMIN only)
  // =========================================================================
  @PostMapping("/runs/{runId}/approve/{taskId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> submitApproval(
      @PathVariable String runId,
      @PathVariable String taskId,
      @Valid @RequestBody ApprovalDecisionRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    agentOrchestrator.submitApprovalForUser(
        runId, taskId, request.approved(), userDetails.getUsername(), request.comment());
    String decision = request.approved() ? "APPROVED" : "REJECTED";
    return ResponseEntity.ok(
        ApiResponse.ok(
            null, "Task " + taskId + " " + decision + " by " + userDetails.getUsername()));
  }

  // =========================================================================
  // Task injection (ADMIN only)
  // =========================================================================
  @PostMapping("/runs/{runId}/inject")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> injectTask(
      @PathVariable String runId, @RequestBody Map<String, Object> body) {
    // Expected body: { "node": { task node spec as returned by listScenarios }, "afterIds": [...] }
    // For simplicity we accept afterIds as a list of strings
    @SuppressWarnings("unchecked")
    List<String> afterIds = (List<String>) body.getOrDefault("afterIds", List.of());
    if (afterIds.size() > 20
        || afterIds.stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9-]{3,50}"))) {
      throw new IllegalArgumentException("afterIds must contain at most 20 valid task IDs");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> nodeSpec = (Map<String, Object>) body.get("node");
    if (nodeSpec == null) {
      return ResponseEntity.badRequest()
          .body(
              ApiResponse.error(
                  "Request body must include 'node' specification", "INVALID_REQUEST"));
    }
    String nodeId = (String) nodeSpec.getOrDefault("id", "INJECTED-" + System.currentTimeMillis());
    String nodeName = (String) nodeSpec.getOrDefault("name", nodeId);
    String stageStr = (String) nodeSpec.getOrDefault("stage", "IMPLEMENTATION");
    if (!nodeId.matches("[A-Za-z0-9-]{3,50}")) {
      throw new IllegalArgumentException("Injected task ID is invalid");
    }
    if (nodeName.isBlank() || nodeName.length() > 200) {
      throw new IllegalArgumentException("Injected task name must contain 1-200 characters");
    }
    TaskNode injectedNode =
        TaskNode.builder()
            .id(nodeId)
            .name(nodeName)
            .stage(
                com.schwab.urlshortener.orchestration.model.TaskStage.valueOf(
                    stageStr.toUpperCase(Locale.ROOT)))
            .dependsOn(afterIds)
            .executor(
                () ->
                    com.schwab.urlshortener.orchestration.model.TaskResult.success(
                        nodeId,
                        "Injected task completed",
                        Map.of(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        1))
            .build();
    agentOrchestrator.injectTask(runId, injectedNode, afterIds);
    return ResponseEntity.ok(
        ApiResponse.ok(null, "Task " + nodeId + " injected into run " + runId));
  }

  // =========================================================================
  // SSE live updates
  // =========================================================================
  /**
   * Returns a Server-Sent Events stream that pushes DAG state updates every 2 seconds. The client
   * can use this to drive a live DAG visualization without polling.
   */
  @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter liveEvents(
      @PathVariable String runId, @AuthenticationPrincipal UserDetails userDetails) {
    SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5-minute timeout
    String username = userDetails.getUsername();
    boolean admin = isAdmin(userDetails);
    AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
    Runnable cancel =
        () -> {
          ScheduledFuture<?> future = futureRef.get();
          if (future != null) future.cancel(false);
        };
    ScheduledFuture<?> future =
        sseScheduler.scheduleAtFixedRate(
            () -> {
              try {
                OrchestrationRunResponse state =
                    agentOrchestrator.getRunState(runId, username, admin);
                String json = objectMapper.writeValueAsString(state);
                emitter.send(
                    SseEmitter.event().name("run-update").data(json, MediaType.APPLICATION_JSON));
                // Stop streaming when run has reached a terminal state
                String status = state.status();
                if ("COMPLETED".equals(status)
                    || "FAILED".equals(status)
                    || "CANCELLED".equals(status)) {
                  emitter.complete();
                  cancel.run();
                }
              } catch (IOException e) {
                emitter.completeWithError(e);
                cancel.run();
              } catch (Exception e) {
                log.error("SSE error for runId={}: {}", runId, e.getMessage());
                try {
                  emitter.send(
                      SseEmitter.event()
                          .name("error")
                          .data(
                              Map.of("error", "Unable to read orchestration state"),
                              MediaType.APPLICATION_JSON));
                } catch (IOException ioEx) {
                  emitter.completeWithError(ioEx);
                  cancel.run();
                }
              }
            },
            0,
            2,
            TimeUnit.SECONDS);
    futureRef.set(future);
    emitter.onCompletion(
        () -> {
          cancel.run();
          log.debug("SSE stream completed for runId={}", runId);
        });
    emitter.onTimeout(
        () -> {
          cancel.run();
          log.debug("SSE stream timed out for runId={}", runId);
        });
    emitter.onError(
        ex -> {
          cancel.run();
          log.warn("SSE stream error for runId={}: {}", runId, ex.getMessage());
        });
    return emitter;
  }

  @PreDestroy
  void shutdownSseScheduler() {
    sseScheduler.shutdownNow();
  }

  private boolean isAdmin(UserDetails userDetails) {
    return userDetails.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
