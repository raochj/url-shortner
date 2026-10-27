package com.schwab.urlshortener.orchestration.scenarios;

import com.schwab.urlshortener.orchestration.TaskGraph;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.analysis.CodebaseImpactAnalyzer;
import com.schwab.urlshortener.orchestration.analysis.CodebaseImpactReport;
import com.schwab.urlshortener.orchestration.guardrails.CompliancePolicyGuardrail;
import com.schwab.urlshortener.orchestration.guardrails.ChangeControlPolicyGuardrail;
import com.schwab.urlshortener.orchestration.guardrails.SecurityPolicyGuardrail;
import com.schwab.urlshortener.orchestration.model.ApprovalGate;
import com.schwab.urlshortener.orchestration.model.ExecutionGate;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brownfield scenario: add QR Code generation to an existing URL shortener. Demonstrates retry
 * behavior (IMPL-B-001 fails on first attempt), guardrail integration, and a blue-green deployment
 * release strategy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrownfieldScenario {
  private final SecurityPolicyGuardrail securityPolicyGuardrail;
  private final CompliancePolicyGuardrail compliancePolicyGuardrail;
  private final ChangeControlPolicyGuardrail changeControlPolicyGuardrail;
  private final CodebaseImpactAnalyzer codebaseImpactAnalyzer;

  @Value("${app.orchestration.demo-task-duration-ms:2000}")
  private long baseDurationMs;

  public TaskGraph buildGraph(Map<String, Object> parameters) {
    String changeIntent =
        String.valueOf(
            parameters == null
                ? "Add governed QR-code generation to the existing URL shortener"
                : parameters.getOrDefault(
                    "changeRequest",
                    "Add governed QR-code generation to the existing URL shortener"));
    String graphId = "brownfield-" + UUID.randomUUID();
    TaskGraph graph = new TaskGraph(graphId, "BROWNFIELD");
    // ---- REQUIREMENTS ---------------------------------------------------
    TaskNode reqB001 =
        TaskNode.builder()
            .id("REQ-B-001")
            .name("Impact analysis for QR code feature")
            .description(
                "Analyze impact of adding QR code generation on existing URL shortener components")
            .stage(TaskStage.REQUIREMENTS)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  CodebaseImpactReport report = codebaseImpactAnalyzer.analyze(changeIntent);
                  return TaskResult.success(
                      "REQ-B-001",
                      "Impact analysis complete",
                      Map.of(
                          "impact-report", report.asArtifact(),
                          "impact-report-markdown", report.markdown(),
                          "impacted-modules", report.impactedModules(),
                          "affected-tests", report.affectedTests()),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- DESIGN ---------------------------------------------------------
    TaskNode designB001 =
        TaskNode.builder()
            .id("DESIGN-B-001")
            .name("Design QR code generation service")
            .description(
                "Design the QrCodeService interface, ZXing integration, and caching strategy")
            .stage(TaskStage.DESIGN)
            .dependsOn("REQ-B-001")
            .entryGate(
                ExecutionGate.requires(
                    "IMPACT-EVIDENCE",
                    "Repository-derived impact analysis is available",
                    "impact-report",
                    "impacted-modules",
                    "affected-tests"))
            .retryPolicy(RetryPolicy.defaultPolicy())
            .guardrail(securityPolicyGuardrail)
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "DESIGN-B-001",
                      "QR code service design complete",
                      Map.of(
                          "design-doc", qrDesignDocument(),
                          "library", "ZXing 3.5.3",
                          "decision-record", "Use a bounded service adapter; preserve URL resolution as the policy boundary"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- IMPLEMENTATION -------------------------------------------------
    // Simulate: first attempt always fails, succeeds on attempt 2
    AtomicInteger implB001Attempts = new AtomicInteger(0);
    TaskNode implB001 =
        TaskNode.builder()
            .id("IMPL-B-001")
            .name("Implement QrCodeService (ZXing)")
            .description(
                "Implement QR code generation using ZXing library with PNG output and caching")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("DESIGN-B-001")
            .retryPolicy(
                new RetryPolicy(
                    3, java.time.Duration.ofMillis(500), 2.0, java.time.Duration.ofSeconds(10)))
            .fallbackExecutor(
                () ->
                    TaskResult.success(
                        "IMPL-B-001",
                        "Fallback adapter enabled using server-side QR rendering",
                        Map.of("fallback", true, "adapter", "server-rendered-svg"),
                        Instant.now(),
                        Instant.now(),
                        3))
            .executor(
                () -> {
                  int attempt = implB001Attempts.incrementAndGet();
                  sleep((long) (baseDurationMs * 1.0));
                  if (attempt == 1) {
                    throw new IOException(
                        "Simulated compile error on first attempt: "
                            + "cannot find symbol QrCodeMatrix in com.google.zxing");
                  }
                  return TaskResult.success(
                      "IMPL-B-001",
                      "QrCodeService implemented successfully on attempt " + attempt,
                      Map.of("class", "QrCodeService.java", "attempt", attempt, "matrixSize", 200),
                      Instant.now(),
                      Instant.now(),
                      attempt);
                })
            .rollbackHandler(
                result -> {
                  log.warn("Rolling back QrCodeService implementation. Removing partial files.");
                  return true;
                })
            .build();
    TaskNode implB002 =
        TaskNode.builder()
            .id("IMPL-B-002")
            .name("Add /qr/{shortCode} endpoint")
            .description(
                "Implement REST endpoint that returns a PNG QR code for a given short code")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("IMPL-B-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "IMPL-B-002",
                      "/qr/{shortCode} endpoint implemented",
                      Map.of("endpoint", "GET /qr/{shortCode}", "contentType", "image/png"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode implB003 =
        TaskNode.builder()
            .id("IMPL-B-003")
            .name("Update ShortenUrlResponse with QR URL")
            .description(
                "Add qrCodeUrl field to ShortenUrlResponse DTO pointing to the /qr/{shortCode}"
                    + " endpoint")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("IMPL-B-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  return TaskResult.success(
                      "IMPL-B-003",
                      "ShortenUrlResponse updated with QR URL field",
                      Map.of("class", "ShortenUrlResponse.java", "newField", "qrCodeUrl"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- TESTING --------------------------------------------------------
    TaskNode testB001 =
        TaskNode.builder()
            .id("TEST-B-001")
            .name("Regression suite for existing flow")
            .description(
                "Run full regression suite to verify QR code addition does not break existing"
                    + " functionality")
            .stage(TaskStage.TESTING)
            .dependsOn("IMPL-B-002", "IMPL-B-003")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .guardrail(compliancePolicyGuardrail)
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "TEST-B-001",
                      "Regression suite passed — no regressions detected",
                      Map.of(
                          "totalTests", 48,
                          "passed", 48,
                          "regressions", 0,
                          "security-scan", "PASSED"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode testB002 =
        TaskNode.builder()
            .id("TEST-B-002")
            .name("QR code generation unit tests")
            .description(
                "Unit tests for QrCodeService covering various URL lengths, special characters, and"
                    + " error cases")
            .stage(TaskStage.TESTING)
            .dependsOn("IMPL-B-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  return TaskResult.success(
                      "TEST-B-002",
                      "QR code unit tests passed",
                      Map.of("tests", 18, "passed", 18, "coverage", "92%"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- DOCUMENTATION --------------------------------------------------
    TaskNode docB001 =
        TaskNode.builder()
            .id("DOC-B-001")
            .name("Update OpenAPI spec")
            .description(
                "Document the new /qr/{shortCode} endpoint and updated ShortenUrlResponse schema")
            .stage(TaskStage.DOCUMENTATION)
            .dependsOn("TEST-B-001", "TEST-B-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.25));
                  return TaskResult.success(
                      "DOC-B-001",
                      "OpenAPI spec updated with QR code endpoint",
                      Map.of("api-spec", qrOpenApiFragment(), "newEndpoints", 1),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    // ---- RELEASE --------------------------------------------------------
    TaskNode relB001 =
        TaskNode.builder()
            .id("REL-B-001")
            .name("Blue-green deploy")
            .description("Blue-green deployment of QR code feature with zero-downtime cutover")
            .stage(TaskStage.RELEASE)
            .dependsOn("DOC-B-001")
            .approvalGate(
                ApprovalGate.adminRequired(
                    "Approve QR feature release: blue-green deployment to production"))
            .entryGate(
                ExecutionGate.requires(
                    "PRODUCTION-READINESS",
                    "Test, security, and documentation evidence present",
                    "testing-completed",
                    "security-scan",
                    "api-spec"))
            .exitGate(
                ExecutionGate.requires(
                    "CUTOVER-EVIDENCE", "Blue-green cutover evidence produced", "strategy"))
            .guardrail(securityPolicyGuardrail)
            .guardrail(compliancePolicyGuardrail)
            .guardrail(changeControlPolicyGuardrail)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .rollbackHandler(
                result -> {
                  log.warn(
                      "REL-B-001 rollback: Reverting to previous Docker image (blue environment)");
                  return true;
                })
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "REL-B-001",
                      "Blue-green deployment completed — traffic switched to green environment",
                      Map.of("strategy", "blue-green", "version", "1.1.0", "downtime", "0s"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    graph.addNode(reqB001);
    graph.addNode(designB001);
    graph.addNode(implB001);
    graph.addNode(implB002);
    graph.addNode(implB003);
    graph.addNode(testB001);
    graph.addNode(testB002);
    graph.addNode(docB001);
    graph.addNode(relB001);
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

  private String qrDesignDocument() {
    return """
        # QR code service design

        - `QrCodeService.render(shortCode, size)` accepts a validated 64-512px size.
        - The controller resolves the short code through the existing URL policy service first.
        - Output is `image/png` with ETag and bounded cache headers; redirect analytics is untouched.
        - ZXing is isolated behind the service interface for fallback and dependency replacement.
        - Invalid, expired, or unauthorized mappings reuse existing error semantics.
        - Release requires regression, security, dependency-license, and rollback evidence.
        """;
  }

  private String qrOpenApiFragment() {
    return """
        /qr/{shortCode}:
          get:
            summary: Render a QR code for an active short URL
            parameters:
              - in: path
                name: shortCode
                required: true
                schema: { type: string, pattern: '^[A-Za-z0-9]{4,10}$' }
            responses:
              '200':
                description: PNG QR code
                content:
                  image/png: { schema: { type: string, format: binary } }
              '404': { description: Unknown or expired short code }
        """;
  }
}
