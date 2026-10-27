package com.schwab.urlshortener.orchestration.scenarios;

import com.schwab.urlshortener.orchestration.TaskGraph;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.guardrails.ChangeControlPolicyGuardrail;
import com.schwab.urlshortener.orchestration.guardrails.CompliancePolicyGuardrail;
import com.schwab.urlshortener.orchestration.guardrails.SecurityPolicyGuardrail;
import com.schwab.urlshortener.orchestration.model.ApprovalGate;
import com.schwab.urlshortener.orchestration.model.ExecutionGate;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Greenfield scenario: build the Schwab URL Shortener from scratch. Produces a 12-node DAG covering
 * requirements through release.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreenfieldScenario {
  private final SecurityPolicyGuardrail securityPolicyGuardrail;
  private final CompliancePolicyGuardrail compliancePolicyGuardrail;
  private final ChangeControlPolicyGuardrail changeControlPolicyGuardrail;
  @Value("${app.orchestration.demo-task-duration-ms:2000}")
  private long baseDurationMs;

  public TaskGraph buildGraph(Map<String, Object> parameters) {
    String graphId = "greenfield-" + UUID.randomUUID();
    TaskGraph graph = new TaskGraph(graphId, "GREENFIELD");
    // ---- REQUIREMENTS (parallel) ----------------------------------------
    TaskNode req001 =
        TaskNode.builder()
            .id("REQ-001")
            .name("Gather stakeholder requirements")
            .description("Collect and document all stakeholder requirements for the URL shortener")
            .stage(TaskStage.REQUIREMENTS)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "REQ-001",
                      "Stakeholder requirements gathered",
                      Map.of("requirements-doc", "requirements-v1.md", "stakeholderCount", 5),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode req002 =
        TaskNode.builder()
            .id("REQ-002")
            .name("Define non-functional requirements")
            .description(
                "Document performance, scalability, security, and availability requirements")
            .stage(TaskStage.REQUIREMENTS)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "REQ-002",
                      "Non-functional requirements defined",
                      Map.of("nfr-doc", "nfr-v1.md", "slaTarget", "99.9%"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- DESIGN ---------------------------------------------------------
    TaskNode design001 =
        TaskNode.builder()
            .id("DESIGN-001")
            .name("Design database schema")
            .description("Design PostgreSQL schema for url_mappings, users, analytics tables")
            .stage(TaskStage.DESIGN)
            .dependsOn("REQ-001", "REQ-002")
            .entryGate(
                ExecutionGate.requires(
                    "DESIGN-EVIDENCE", "Requirements baselined", "requirements-doc", "nfr-doc"))
            .approvalGate(
                ApprovalGate.adminRequired(
                    "Approve DB schema design before proceeding to implementation"))
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "DESIGN-001",
                      "Database schema designed and reviewed",
                      Map.of("schema-doc", "db-schema-v1.sql", "tableCount", 4),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .exitGate(
                ExecutionGate.requires("SCHEMA-REVIEW", "Schema artifact produced", "schema-doc"))
            .build();
    TaskNode design002 =
        TaskNode.builder()
            .id("DESIGN-002")
            .name("Design Redis caching strategy")
            .description(
                "Design caching strategy for short code lookup and click count aggregation")
            .stage(TaskStage.DESIGN)
            .dependsOn("REQ-001", "REQ-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "DESIGN-002",
                      "Redis caching strategy designed",
                      Map.of("cache-doc", "caching-strategy.md", "ttlSeconds", 604800),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- IMPLEMENTATION -------------------------------------------------
    TaskNode impl001 =
        TaskNode.builder()
            .id("IMPL-001")
            .name("Implement Base62 encoder")
            .description("Implement Base62 encoding/decoding utility for short code generation")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("DESIGN-001", "DESIGN-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  return TaskResult.success(
                      "IMPL-001",
                      "Base62Encoder implemented",
                      Map.of("class", "Base62Encoder.java", "coverage", "95%"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode impl002 =
        TaskNode.builder()
            .id("IMPL-002")
            .name("Implement UrlMappingService")
            .description("Implement core URL shortening, retrieval, and management service")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("IMPL-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "IMPL-002",
                      "UrlMappingService implemented",
                      Map.of("class", "UrlShortenerService.java", "methodCount", 7),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode impl003 =
        TaskNode.builder()
            .id("IMPL-003")
            .name("Implement RedirectController")
            .description("Implement HTTP redirect endpoint with analytics recording")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("IMPL-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "IMPL-003",
                      "RedirectController implemented",
                      Map.of("class", "RedirectController.java", "endpoint", "GET /{shortCode}"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode impl004 =
        TaskNode.builder()
            .id("IMPL-004")
            .name("Implement AnalyticsService")
            .description("Implement async click tracking and analytics aggregation service")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("DESIGN-001", "DESIGN-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "IMPL-004",
                      "AnalyticsService implemented",
                      Map.of("class", "AnalyticsService.java", "asyncEnabled", true),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- TESTING --------------------------------------------------------
    TaskNode test001 =
        TaskNode.builder()
            .id("TEST-001")
            .name("Unit tests for Base62Encoder")
            .description("Write and execute unit tests covering all Base62 encoding edge cases")
            .stage(TaskStage.TESTING)
            .dependsOn("IMPL-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  return TaskResult.success(
                      "TEST-001",
                      "Base62Encoder unit tests passed",
                      Map.of("tests", 24, "passed", 24, "coverage", "97%"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode test002 =
        TaskNode.builder()
            .id("TEST-002")
            .name("Integration tests for redirect flow")
            .description(
                "Integration tests verifying the full URL shorten → redirect → analytics flow")
            .stage(TaskStage.TESTING)
            .dependsOn("IMPL-003")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "TEST-002",
                      "Integration tests passed",
                      Map.of(
                          "tests", 12,
                          "passed", 12,
                          "integrationCoverage", "88%",
                          "security-scan", "PASSED"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- DOCUMENTATION --------------------------------------------------
    TaskNode doc001 =
        TaskNode.builder()
            .id("DOC-001")
            .name("Generate OpenAPI specification")
            .description("Generate and publish OpenAPI 3.0 specification for all REST endpoints")
            .stage(TaskStage.DOCUMENTATION)
            .dependsOn("IMPL-001", "IMPL-002", "IMPL-003", "IMPL-004")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  return TaskResult.success(
                      "DOC-001",
                      "OpenAPI specification generated",
                      Map.of("api-spec", "openapi-v1.yaml", "endpointCount", 9),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- RELEASE --------------------------------------------------------
    TaskNode rel001 =
        TaskNode.builder()
            .id("REL-001")
            .name("Deploy to staging")
            .description("Deploy application to staging environment and run smoke tests")
            .stage(TaskStage.RELEASE)
            .dependsOn("TEST-001", "TEST-002", "DOC-001")
            .approvalGate(
                ApprovalGate.adminRequired(
                    "Approve staging deployment: verify all tests pass and docs are complete"))
            .entryGate(
                ExecutionGate.requires(
                    "RELEASE-EVIDENCE",
                    "Tests, security scan, and documentation complete",
                    "testing-completed",
                    "security-scan",
                    "api-spec"))
            .exitGate(
                ExecutionGate.requires(
                    "HEALTH-CHECK", "Deployment health check passed", "healthCheck"))
            .guardrail(securityPolicyGuardrail)
            .guardrail(compliancePolicyGuardrail)
            .guardrail(changeControlPolicyGuardrail)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .rollbackHandler(
                result -> {
                  log.warn(
                      "Rolling back deployment for run task REL-001. Result success={}",
                      result.success());
                  return true;
                })
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "REL-001",
                      "Deployed to staging successfully",
                      Map.of("environment", "staging", "version", "1.0.0", "healthCheck", "PASSED"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // Add all nodes
    graph.addNode(req001);
    graph.addNode(req002);
    graph.addNode(design001);
    graph.addNode(design002);
    graph.addNode(impl001);
    graph.addNode(impl002);
    graph.addNode(impl003);
    graph.addNode(impl004);
    graph.addNode(test001);
    graph.addNode(test002);
    graph.addNode(doc001);
    graph.addNode(rel001);
    graph.validate();
    return graph;
  }

  private void sleep(long ms) {
    if (ms <= 0) return;
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
