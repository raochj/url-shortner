package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.model.ReliabilityMetrics;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tracks timing and reliability data for orchestration runs.
 *
 * <p>Each entry is a long[4]: [startMs, endMs, failMs, recoverMs]. Index constants: 0 = startMs 1 =
 * endMs 2 = failMs 3 = recoverMs
 */
@Slf4j
@Component
public class ReliabilityTracker {
  private static final int IDX_START = 0;
  private static final int IDX_END = 1;
  private static final int IDX_FAIL = 2;
  private static final int IDX_RECOVER = 3;
  // Key: "runId:taskId" -> timing array
  private final ConcurrentHashMap<String, long[]> timings = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // Recording methods
  // -------------------------------------------------------------------------
  public void recordStart(String runId, String taskId) {
    String key = key(runId, taskId);
    timings.compute(
        key,
        (k, current) -> {
          long[] next = current == null ? new long[4] : current.clone();
          next[IDX_START] = System.currentTimeMillis();
          next[IDX_END] = 0L;
          return next;
        });
    log.debug("ReliabilityTracker: recordStart key={}", key);
  }

  public void recordComplete(String runId, String taskId) {
    String key = key(runId, taskId);
    timings.compute(
        key,
        (k, entry) -> {
          long[] next = entry == null ? new long[4] : entry.clone();
          next[IDX_END] = System.currentTimeMillis();
          return next;
        });
    log.debug("ReliabilityTracker: recordComplete key={}", key);
  }

  public void recordFailure(String runId, String taskId) {
    String key = key(runId, taskId);
    timings.compute(
        key,
        (k, entry) -> {
          long[] next = entry == null ? new long[4] : entry.clone();
          if (next[IDX_FAIL] == 0L) next[IDX_FAIL] = System.currentTimeMillis();
          return next;
        });
    log.debug("ReliabilityTracker: recordFailure key={}", key);
  }

  public void recordRecovery(String runId, String taskId) {
    String key = key(runId, taskId);
    timings.compute(
        key,
        (k, entry) -> {
          long[] next = entry == null ? new long[4] : entry.clone();
          next[IDX_RECOVER] = System.currentTimeMillis();
          return next;
        });
    log.debug("ReliabilityTracker: recordRecovery key={}", key);
  }

  // -------------------------------------------------------------------------
  // Metrics snapshot
  // -------------------------------------------------------------------------
  public ReliabilityMetrics snapshot(String runId, TaskGraph graph) {
    int totalTasks = graph.getNodes().size();
    int completedTasks = 0;
    int failedTasks = 0;
    int rolledBackTasks = 0;
    int retryCount = 0;
    int approvalsPending = 0;
    int approvalsGranted = 0;
    for (TaskNode node : graph.getNodes().values()) {
      retryCount += Math.max(0, node.getAttemptCount() - 1);
      TaskStatus status = node.getStatus();
      switch (status) {
        case COMPLETED -> completedTasks++;
        case FAILED -> failedTasks++;
        case ROLLED_BACK -> rolledBackTasks++;
        case AWAITING_APPROVAL -> approvalsPending++;
        case APPROVED -> approvalsGranted++;
        default -> {
          /* no-op */
        }
      }
    }
    double successRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100.0 : 0.0;
    double rollbackRate = totalTasks > 0 ? (double) rolledBackTasks / totalTasks * 100.0 : 0.0;
    // Collect latency values (endMs - startMs) for tasks that completed
    List<Long> latencies = new ArrayList<>();
    List<Long> mttrValues = new ArrayList<>();
    long totalElapsedMs = 0L;
    long runStart = Long.MAX_VALUE;
    long runEnd = 0L;
    for (Map.Entry<String, long[]> entry : timings.entrySet()) {
      if (!entry.getKey().startsWith(runId + ":")) continue;
      long[] t = entry.getValue();
      if (t[IDX_START] > 0) {
        runStart = Math.min(runStart, t[IDX_START]);
      }
      if (t[IDX_END] > 0) {
        runEnd = Math.max(runEnd, t[IDX_END]);
        long latency = t[IDX_END] - t[IDX_START];
        if (latency >= 0) latencies.add(latency);
      }
      // MTTR: time from failure to recovery
      if (t[IDX_FAIL] > 0 && t[IDX_RECOVER] > 0 && t[IDX_RECOVER] >= t[IDX_FAIL]) {
        mttrValues.add(t[IDX_RECOVER] - t[IDX_FAIL]);
      }
    }
    if (runStart != Long.MAX_VALUE && runEnd > 0) {
      totalElapsedMs = runEnd - runStart;
    }
    long p50 = 0L;
    long p95 = 0L;
    if (!latencies.isEmpty()) {
      long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
      p50 = sorted[(int) Math.floor(sorted.length * 0.50)];
      p95 = sorted[Math.min((int) Math.ceil(sorted.length * 0.95) - 1, sorted.length - 1)];
    }
    long mttr = 0L;
    if (!mttrValues.isEmpty()) {
      mttr = (long) mttrValues.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    return new ReliabilityMetrics(
        runId,
        totalTasks,
        completedTasks,
        failedTasks,
        rolledBackTasks,
        retryCount,
        successRate,
        rollbackRate,
        totalElapsedMs,
        mttr,
        p50,
        p95,
        approvalsPending,
        approvalsGranted);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------
  private String key(String runId, String taskId) {
    return runId + ":" + taskId;
  }

  public void removeRun(String runId) {
    String prefix = runId + ":";
    timings.keySet().removeIf(key -> key.startsWith(prefix));
  }
}
