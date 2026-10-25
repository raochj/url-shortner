package com.schwab.urlshortener.orchestration.guardrails;

import com.schwab.urlshortener.orchestration.PolicyGuardrail;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RateLimitPolicyGuardrail implements PolicyGuardrail {
  private static final int MAX_CONCURRENT_IMPL = 4;

  @Override
  public String getName() {
    return "RateLimitPolicy";
  }

  @Override
  public List<PolicyViolation> evaluate(TaskNode node, Map<String, Object> context) {
    List<PolicyViolation> violations = new ArrayList<>();
    Object concurrentCountObj = context.get("concurrent-impl-count");
    if (concurrentCountObj != null) {
      int concurrentCount;
      if (concurrentCountObj instanceof Number n) {
        concurrentCount = n.intValue();
      } else {
        try {
          concurrentCount = Integer.parseInt(concurrentCountObj.toString());
        } catch (NumberFormatException e) {
          return violations;
        }
      }
      if (concurrentCount > MAX_CONCURRENT_IMPL) {
        violations.add(
            new PolicyViolation(
                getName(),
                "RATE-001",
                "Concurrent implementation task count ("
                    + concurrentCount
                    + ") exceeds the allowed maximum of "
                    + MAX_CONCURRENT_IMPL
                    + ". Throttle implementation task concurrency to avoid resource exhaustion "
                    + "and maintain code review quality.",
                PolicyViolation.Severity.BLOCKING));
      }
    }
    return violations;
  }
}
