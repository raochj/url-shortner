package com.schwab.urlshortener.dto.response;

import java.time.OffsetDateTime;

public record OrchestrationRunSummaryResponse(
    String runId,
    String scenarioType,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    String initiatedBy) {}
