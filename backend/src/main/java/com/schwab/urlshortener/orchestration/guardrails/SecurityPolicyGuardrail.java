package com.schwab.urlshortener.orchestration.guardrails;

import com.schwab.urlshortener.orchestration.PolicyGuardrail;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SecurityPolicyGuardrail implements PolicyGuardrail {
  @Override
  public String getName() {
    return "SecurityPolicy";
  }

  @Override
  public List<PolicyViolation> evaluate(TaskNode node, Map<String, Object> context) {
    List<PolicyViolation> violations = new ArrayList<>();
    // BLOCKING: RELEASE stage nodes require a security scan artifact in context
    if (node.getStage() == TaskStage.RELEASE) {
      boolean hasSecurityScan = context.containsKey("security-scan");
      if (!hasSecurityScan) {
        violations.add(
            new PolicyViolation(
                getName(),
                "SEC-001",
                "Release stage tasks require a completed 'security-scan' artifact in the pipeline"
                    + " context. Ensure a security scanning task has run and produced the"
                    + " 'security-scan' artifact before releasing.",
                PolicyViolation.Severity.BLOCKING));
      }
    }
    // WARNING: DESIGN stage nodes whose description mentions "external API"
    if (node.getStage() == TaskStage.DESIGN) {
      String desc =
          node.getDescription() != null ? node.getDescription().toLowerCase(Locale.ROOT) : "";
      if (desc.contains("external api") || desc.contains("external-api")) {
        violations.add(
            new PolicyViolation(
                getName(),
                "SEC-002",
                "Design node '"
                    + node.getName()
                    + "' references an external API. Ensure proper authentication, rate limiting,"
                    + " and data classification review before implementation.",
                PolicyViolation.Severity.WARNING));
      }
    }
    return violations;
  }
}
