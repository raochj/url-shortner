package com.schwab.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskGraphTest {
  @Test
  void validateRejectsUnknownDependency() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    graph.addNode(node("B", List.of("missing")));

    assertThatThrownBy(graph::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown dependency");
  }

  @Test
  void validateRejectsCycle() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    graph.addNode(node("A", List.of("B")));
    graph.addNode(node("B", List.of("A")));

    assertThatThrownBy(graph::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cycle detected");
  }

  @Test
  void failedInjectionIsRolledBackFromGraph() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    graph.addNode(node("A", List.of()));

    assertThatThrownBy(() -> graph.injectNode(node("B", List.of("B")), List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(graph.getNodes()).containsOnlyKeys("A");
  }

  private TaskNode node(String id, List<String> dependencies) {
    return TaskNode.builder()
        .id(id)
        .stage(TaskStage.IMPLEMENTATION)
        .dependsOn(dependencies)
        .executor(() -> TaskResult.success(id, "done", Map.of(), Instant.now(), Instant.now(), 1))
        .build();
  }
}
