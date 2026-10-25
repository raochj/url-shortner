package com.schwab.urlshortener.exception;

public class ApprovalRequiredException extends RuntimeException {
  private final String runId;
  private final String taskId;

  public ApprovalRequiredException(String runId, String taskId) {
    super("Approval required for task '" + taskId + "' in run '" + runId + "'");
    this.runId = runId;
    this.taskId = taskId;
  }

  public ApprovalRequiredException(String runId, String taskId, String prompt) {
    super("Approval required for task '" + taskId + "' in run '" + runId + "': " + prompt);
    this.runId = runId;
    this.taskId = taskId;
  }

  public String getRunId() {
    return runId;
  }

  public String getTaskId() {
    return taskId;
  }
}
