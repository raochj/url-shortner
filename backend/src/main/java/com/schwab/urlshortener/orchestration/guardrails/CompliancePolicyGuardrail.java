package com.schwab.urlshortener.orchestration.guardrails;

import com.schwab.urlshortener.orchestration.PolicyGuardrail;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.model.PolicyViolation;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompliancePolicyGuardrail implements PolicyGuardrail {
  @Override
  public String getName() {
    return "CompliancePolicy";
  }

  @Override
  public List<PolicyViolation> evaluate(TaskNode node, Map<String, Object> context) {
    List<PolicyViolation> violations = new ArrayList<>();
    // BLOCKING: RELEASE stage requires testing to have been completed
    if (node.getStage() == TaskStage.RELEASE) {
      Object testingCompleted = context.get("testing-completed");
      boolean isTestingDone =
          testingCompleted instanceof Boolean b
              ? b
              : testingCompleted != null && Boolean.parseBoolean(testingCompleted.toString());
      if (!isTestingDone) {
        violations.add(
            new PolicyViolation(
                getName(),
                "COMP-001",
                "Release stage task '"
                    + node.getName()
                    + "' cannot proceed without a completed TESTING stage. "
                    + "Set 'testing-completed=true' in the pipeline context once all tests pass.",
                PolicyViolation.Severity.BLOCKING));
      }
    }
    // WARNING: DOCUMENTATION stage nodes should have an API spec in context
    if (node.getStage() == TaskStage.DOCUMENTATION && !context.containsKey("api-spec")) {
      violations.add(
          new PolicyViolation(
              getName(),
              "COMP-002",
              "Documentation task '"
                  + node.getName()
                  + "' is missing the 'api-spec' artifact in context. "
                  + "Provide the OpenAPI specification artifact before generating documentation.",
              PolicyViolation.Severity.WARNING));
    }
    return violations;
  }
}
