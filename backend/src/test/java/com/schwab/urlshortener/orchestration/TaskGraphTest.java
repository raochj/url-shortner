package com.schwab.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
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

  @Test
  void topologicalOrderIsDependencyFirstAndStable() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    graph.addNode(node("C", List.of("A", "B")));
    graph.addNode(node("B", List.of("A")));
    graph.addNode(node("A", List.of()));

    assertThat(graph.topologicalOrder()).extracting(TaskNode::getId).containsExactly("A", "B", "C");
  }

  @Test
  void replanInvalidatesOnlyDescendantsAndVersionsContext() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    TaskNode source = node("SOURCE", List.of());
    TaskNode derived = node("DERIVED", List.of("SOURCE"));
    TaskNode independent = node("INDEPENDENT", List.of());
    graph.addNode(source);
    graph.addNode(derived);
    graph.addNode(independent);
    source.transitionTo(TaskStatus.COMPLETED);
    derived.transitionTo(TaskStatus.COMPLETED);
    independent.transitionTo(TaskStatus.COMPLETED);

    assertThat(graph.invalidateDescendants("SOURCE")).containsExactly("DERIVED");
    assertThat(derived.getStatus()).isEqualTo(TaskStatus.PENDING);
    assertThat(independent.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(graph.getContextVersion()).isEqualTo(2L);
  }

  @Test
  void completedFallbackSatisfiesDownstreamDependency() {
    TaskGraph graph = new TaskGraph("graph", "TEST");
    TaskNode fallback = node("FALLBACK", List.of());
    TaskNode downstream = node("DOWNSTREAM", List.of("FALLBACK"));
    graph.addNode(fallback);
    graph.addNode(downstream);
    fallback.transitionTo(TaskStatus.FALLBACK_COMPLETED);

    assertThat(graph.getReadyNodes(graph.getCompletedNodeIds()))
        .extracting(TaskNode::getId)
        .containsExactly("DOWNSTREAM");
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
