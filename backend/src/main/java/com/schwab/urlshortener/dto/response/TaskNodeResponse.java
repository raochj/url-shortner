package com.schwab.urlshortener.dto.response;

import java.util.List;

public record TaskNodeResponse(
    String taskId,
    String name,
    String description,
    String stage,
    String status,
    int attemptNumber,
    Long latencyMs,
    List<String> dependencies,
    String errorDetail,
    boolean requiresApproval,
    String approvalPrompt,
    List<String> entryGates,
    List<String> exitGates) {
  public TaskNodeResponse {
    dependencies = List.copyOf(dependencies);
    entryGates = List.copyOf(entryGates);
    exitGates = List.copyOf(exitGates);
  }
}
