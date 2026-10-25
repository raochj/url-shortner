package com.schwab.urlshortener.orchestration.model;

public record ReliabilityMetrics(
    String runId,
    int totalTasks,
    int completedTasks,
    int failedTasks,
    int rolledBackTasks,
    int retryCount,
    double successRate,
    double rollbackRate,
    long totalElapsedMs,
    long meanTimeToRecoverMs,
    long p50LatencyMs,
    long p95LatencyMs,
    int approvalsPending,
    int approvalsGranted) {}
