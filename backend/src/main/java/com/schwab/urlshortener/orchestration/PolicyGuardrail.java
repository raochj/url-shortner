package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import java.util.List;
import java.util.Map;

public interface PolicyGuardrail {
  String getName();

  List<PolicyViolation> evaluate(TaskNode node, Map<String, Object> context);

  default boolean isBlocking(List<PolicyViolation> violations) {
    return violations.stream().anyMatch(v -> v.severity() == PolicyViolation.Severity.BLOCKING);
  }
}
