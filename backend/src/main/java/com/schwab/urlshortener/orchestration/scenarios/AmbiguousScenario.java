package com.schwab.urlshortener.orchestration.scenarios;

import com.schwab.urlshortener.orchestration.TaskGraph;
import com.schwab.urlshortener.orchestration.TaskNode;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ambiguous scenario: performance optimization with dynamic task injection.
 *
 * <p>The graph starts with a single clarification task (CLARIFY-001). Once that task completes,
 * AgentOrchestrator detects the completion and calls getInjectionTasks() to inject the 4 follow-on
 * optimization tasks into the live graph.
 */
@Slf4j
@Component
public class AmbiguousScenario {
  @Value("${app.orchestration.demo-task-duration-ms:2000}")
  private long baseDurationMs;

  /** Builds the initial graph containing only the clarification node. */
  public TaskGraph buildGraph(Map<String, Object> parameters) {
    String graphId = "ambiguous-" + UUID.randomUUID();
    TaskGraph graph = new TaskGraph(graphId, "AMBIGUOUS");
    graph.setDynamicExpansionPending(true);
    String goal =
        parameters != null
            ? (String) parameters.getOrDefault("goal", "Improve P95 redirect latency to under 50ms")
            : "Improve P95 redirect latency to under 50ms";
    TaskNode clarify001 =
        TaskNode.builder()
            .id("CLARIFY-001")
            .name("Clarify performance requirements")
            .description(
                "Analyze the stated goal, identify the primary bottleneck, and form a hypothesis: "
                    + goal)
            .stage(TaskStage.REQUIREMENTS)
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.5));
                  log.info(
                      "CLARIFY-001: Analyzing goal='{}'. Hypothesis: Redis miss rate is the"
                          + " bottleneck.",
                      goal);
                  return TaskResult.success(
                      "CLARIFY-001",
                      "Clarification complete — hypothesis formed",
                      Map.of(
                          "hypothesis", "cache-warming",
                          "targetP95Ms", 50,
                          "bottleneck", "redis-miss-rate",
                          "goal", goal,
                          "strategy", "implement-cache-warming-and-pool-tuning"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    graph.addNode(clarify001);
    // NOTE: no validate() call here because injection tasks don't exist yet
    return graph;
  }

  /**
   * Returns the 4 follow-on tasks to inject once CLARIFY-001 completes. The caller
   * (AgentOrchestrator) will add CLARIFY-001 as an extra dependency via TaskGraph.injectNode().
   */
  public List<TaskNode> getInjectionTasks(Map<String, Object> clarificationArtifacts) {
    log.info(
        "AmbiguousScenario: building injection tasks from artifacts={}", clarificationArtifacts);
    TaskNode implOpt001 =
        TaskNode.builder()
            .id("IMPL-OPT-001")
            .name("Implement Redis cache warming")
            .description(
                "Pre-populate Redis cache with top-100 most-accessed short codes on startup and on"
                    + " schedule")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("CLARIFY-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  return TaskResult.success(
                      "IMPL-OPT-001",
                      "Redis cache warming implemented",
                      Map.of("class", "CacheWarmingService.java", "topN", 100),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode implOpt002 =
        TaskNode.builder()
            .id("IMPL-OPT-002")
            .name("Tune database connection pool")
            .description(
                "Increase HikariCP pool size and tune connection timeout to reduce DB wait times")
            .stage(TaskStage.IMPLEMENTATION)
            .dependsOn("CLARIFY-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.75));
                  return TaskResult.success(
                      "IMPL-OPT-002",
                      "DB connection pool tuned",
                      Map.of("maxPoolSize", 30, "connectionTimeout", 15000, "idleTimeout", 300000),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode testOpt001 =
        TaskNode.builder()
            .id("TEST-OPT-001")
            .name("Benchmark after optimization")
            .description(
                "Run load test comparing P50/P95/P99 latencies before and after optimization"
                    + " changes")
            .stage(TaskStage.TESTING)
            .dependsOn("IMPL-OPT-001", "IMPL-OPT-002")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 1.0));
                  long targetP95 = 50L;
                  Object target =
                      clarificationArtifacts != null
                          ? clarificationArtifacts.get("targetP95Ms")
                          : null;
                  if (target instanceof Number n) targetP95 = n.longValue();
                  return TaskResult.success(
                      "TEST-OPT-001",
                      "Benchmark passed — P95 latency within target",
                      Map.of(
                          "p50Ms",
                          8L,
                          "p95Ms",
                          targetP95 - 5,
                          "p99Ms",
                          targetP95 + 10,
                          "targetP95Ms",
                          targetP95,
                          "passed",
                          true),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    TaskNode docOpt001 =
        TaskNode.builder()
            .id("DOC-OPT-001")
            .name("Document performance improvements")
            .description(
                "Document the changes made, benchmarks before/after, and operational runbook for"
                    + " cache warming")
            .stage(TaskStage.DOCUMENTATION)
            .dependsOn("TEST-OPT-001")
            .retryPolicy(RetryPolicy.defaultPolicy())
            .executor(
                () -> {
                  sleep((long) (baseDurationMs * 0.25));
                  return TaskResult.success(
                      "DOC-OPT-001",
                      "Performance improvement documentation complete",
                      Map.of("doc", "performance-improvements.md", "api-spec", "openapi-unchanged"),
                      Instant.now(),
                      Instant.now(),
                      1);
                })
            .build();
    return List.of(implOpt001, implOpt002, testOpt001, docOpt001);
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
