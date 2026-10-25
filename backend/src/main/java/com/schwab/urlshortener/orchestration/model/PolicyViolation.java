package com.schwab.urlshortener.orchestration.model;

public record PolicyViolation(
    String policyName, String ruleId, String description, Severity severity) {
  public enum Severity {
    INFO,
    WARNING,
    BLOCKING
  }
}
