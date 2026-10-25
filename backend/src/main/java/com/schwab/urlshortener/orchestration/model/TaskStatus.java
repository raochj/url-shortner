package com.schwab.urlshortener.orchestration.model;

public enum TaskStatus {
  PENDING,
  IN_PROGRESS,
  AWAITING_APPROVAL,
  APPROVED,
  COMPLETED,
  FAILED,
  ROLLED_BACK,
  SKIPPED
}
