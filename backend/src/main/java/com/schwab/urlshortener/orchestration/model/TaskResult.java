package com.schwab.urlshortener.orchestration.model;

import java.time.Instant;
import java.util.Map;

public record TaskResult(
    String taskId,
    boolean success,
    String message,
    Map<String, Object> artifacts,
    Instant startTime,
    Instant endTime,
    int attemptNumber,
    String errorDetail) {
  public TaskResult {
    artifacts = Map.copyOf(artifacts);
  }

  public long latencyMillis() {
    return endTime.toEpochMilli() - startTime.toEpochMilli();
  }

  public static TaskResult success(
      String taskId,
      String message,
      Map<String, Object> artifacts,
      Instant start,
      Instant end,
      int attempt) {
    return new TaskResult(taskId, true, message, artifacts, start, end, attempt, null);
  }

  public static TaskResult failure(
      String taskId, String errorDetail, Instant start, Instant end, int attempt) {
    return new TaskResult(taskId, false, "Task failed", Map.of(), start, end, attempt, errorDetail);
  }
}
