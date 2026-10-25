package com.schwab.urlshortener.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record OrchestrationRunResponse(
    String runId,
    String scenarioType,
    String status,
    List<TaskNodeResponse> tasks,
    Map<String, Object> parameters,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    ReliabilityMetricsResponse metrics) {
  public OrchestrationRunResponse {
    tasks = List.copyOf(tasks);
    parameters = Map.copyOf(parameters);
  }
}
