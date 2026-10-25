package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.model.TaskStage;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TaskGraph {
  private final Map<String, TaskNode> nodes = new ConcurrentHashMap<>();
  private final AtomicBoolean dynamicExpansionPending = new AtomicBoolean(false);
  private final String graphId;
  private final String scenarioType;

  public TaskGraph(String graphId, String scenarioType) {
    this.graphId = graphId;
    this.scenarioType = scenarioType;
  }

  public String getGraphId() {
    return graphId;
  }

  public String getScenarioType() {
    return scenarioType;
  }

  public Map<String, TaskNode> getNodes() {
    return Map.copyOf(nodes);
  }

  public boolean isDynamicExpansionPending() {
    return dynamicExpansionPending.get();
  }

  public void setDynamicExpansionPending(boolean pending) {
    dynamicExpansionPending.set(pending);
  }

  public void addNode(TaskNode node) {
    nodes.put(node.getId(), node);
  }

  /**
   * Validates the graph for cycles using iterative DFS (post-order). Throws IllegalStateException
   * if a cycle or unresolved dependency is detected.
   */
  public void validate() {
    // Check all dependency IDs reference existing nodes
    for (TaskNode node : nodes.values()) {
      for (String depId : node.getDependencyIds()) {
        if (!nodes.containsKey(depId)) {
          throw new IllegalStateException(
              "Node '" + node.getId() + "' references unknown dependency '" + depId + "'");
        }
      }
    }
    // Cycle detection via DFS coloring: 0=unvisited, 1=in-stack, 2=done
    Map<String, Integer> color = new HashMap<>();
    for (String id : nodes.keySet()) color.put(id, 0);
    for (String startId : nodes.keySet()) {
      if (color.get(startId) == 0) {
        detectCycle(startId, color);
      }
    }
  }

  private void detectCycle(String nodeId, Map<String, Integer> color) {
    color.put(nodeId, 1);
    TaskNode node = nodes.get(nodeId);
    for (String depId : node.getDependencyIds()) {
      if (color.get(depId) == 1) {
        throw new IllegalStateException(
            "Cycle detected involving node '" + nodeId + "' -> '" + depId + "'");
      }
      if (color.get(depId) == 0) {
        detectCycle(depId, color);
      }
    }
    color.put(nodeId, 2);
  }

  /** Returns nodes whose all dependencies are in completedIds and whose status is PENDING. */
  public List<TaskNode> getReadyNodes(Set<String> completedIds) {
    return nodes.values().stream()
        .filter(n -> n.isReadyToExecute(completedIds))
        .collect(Collectors.toList());
  }

  public List<TaskNode> getNodesByStage(TaskStage stage) {
    return nodes.values().stream().filter(n -> n.getStage() == stage).collect(Collectors.toList());
  }

  public Set<String> getCompletedNodeIds() {
    return nodes.values().stream()
        .filter(n -> n.getStatus() == TaskStatus.COMPLETED || n.getStatus() == TaskStatus.SKIPPED)
        .map(TaskNode::getId)
        .collect(Collectors.toSet());
  }

  public boolean isComplete() {
    return !dynamicExpansionPending.get()
        && nodes.values().stream()
            .allMatch(
                n ->
                    n.getStatus() == TaskStatus.COMPLETED
                        || n.getStatus() == TaskStatus.SKIPPED
                        || n.getStatus() == TaskStatus.FAILED
                        || n.getStatus() == TaskStatus.ROLLED_BACK);
  }

  public boolean hasAnyFailed() {
    return nodes.values().stream().anyMatch(n -> n.getStatus() == TaskStatus.FAILED);
  }

  /**
   * Injects a new node, adding afterIds as additional dependencies on top of newNode's existing
   * ones.
   */
  @SuppressFBWarnings(
      value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
      justification =
          "A failed validation is rethrown after atomically removing the injected node.")
  public synchronized void injectNode(TaskNode newNode, List<String> afterIds) {
    if (nodes.containsKey(newNode.getId())) {
      throw new IllegalArgumentException("Task already exists: " + newNode.getId());
    }
    // afterIds are treated as additional dependencies; the node builder already sets deps
    // For simplicity we rebuild the node with merged deps
    List<String> allDeps = new ArrayList<>(newNode.getDependencyIds());
    for (String afterId : afterIds) {
      if (!nodes.containsKey(afterId)) {
        throw new IllegalArgumentException("Unknown injection dependency: " + afterId);
      }
      if (!allDeps.contains(afterId)) {
        allDeps.add(afterId);
      }
    }
    // Recreate node with merged dependencies (the node may already have them; this is idempotent)
    TaskNode merged =
        TaskNode.builder()
            .id(newNode.getId())
            .name(newNode.getName())
            .description(newNode.getDescription())
            .stage(newNode.getStage())
            .dependsOn(allDeps)
            .executor(newNode.getExecutor())
            .rollbackHandler(newNode.getRollbackHandler())
            .retryPolicy(newNode.getRetryPolicy())
            .approvalGate(newNode.getApprovalGate())
            .guardrails(newNode.getGuardrails())
            .build();
    nodes.put(merged.getId(), merged);
    try {
      validate();
    } catch (RuntimeException ex) {
      nodes.remove(merged.getId(), merged);
      throw ex;
    }
  }

  /** Marks a node as SKIPPED. */
  public void bypassNode(String nodeId) {
    TaskNode node = nodes.get(nodeId);
    if (node != null) {
      node.transitionTo(TaskStatus.SKIPPED);
    }
  }

  /** Returns a serializable map representation for JSON storage (dag_snapshot). */
  public Map<String, Object> serialize() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("graphId", graphId);
    snapshot.put("scenarioType", scenarioType);
    List<Map<String, Object>> nodeList = new ArrayList<>();
    for (TaskNode node : nodes.values()) {
      Map<String, Object> n = new LinkedHashMap<>();
      n.put("id", node.getId());
      n.put("name", node.getName());
      n.put("description", node.getDescription());
      n.put("stage", node.getStage().name());
      n.put("status", node.getStatus().name());
      n.put("dependencies", node.getDependencyIds());
      n.put("attemptCount", node.getAttemptCount());
      if (node.getLastResult() != null) {
        n.put("artifacts", node.getLastResult().artifacts());
      }
      nodeList.add(n);
    }
    snapshot.put("nodes", nodeList);
    return snapshot;
  }

  /** Returns a visualization structure with positioned nodes and directed edges. */
  public Map<String, Object> getDagVisualization() {
    // Simple layered layout: group by stage order, assign x/y positions
    TaskStage[] stageOrder = TaskStage.values();
    int stageGapX = 250;
    int nodeGapY = 120;
    List<Map<String, Object>> vizNodes = new ArrayList<>();
    Map<String, Integer[]> nodePositions = new HashMap<>();
    for (int si = 0; si < stageOrder.length; si++) {
      TaskStage stage = stageOrder[si];
      List<TaskNode> stageNodes = getNodesByStage(stage);
      int total = stageNodes.size();
      int startY = -(total - 1) * nodeGapY / 2;
      for (int ni = 0; ni < stageNodes.size(); ni++) {
        TaskNode node = stageNodes.get(ni);
        int x = si * stageGapX + 100;
        int y = startY + ni * nodeGapY + 400;
        nodePositions.put(node.getId(), new Integer[] {x, y});
        Map<String, Object> vizNode = new LinkedHashMap<>();
        vizNode.put("id", node.getId());
        vizNode.put("name", node.getName());
        vizNode.put("stage", node.getStage().name());
        vizNode.put("status", node.getStatus().name());
        vizNode.put("x", x);
        vizNode.put("y", y);
        vizNode.put("attemptCount", node.getAttemptCount());
        vizNode.put("requiresApproval", node.getApprovalGate().required());
        vizNodes.add(vizNode);
      }
    }
    List<Map<String, Object>> edges = new ArrayList<>();
    for (TaskNode node : nodes.values()) {
      for (String depId : node.getDependencyIds()) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", depId);
        edge.put("target", node.getId());
        edges.add(edge);
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("nodes", vizNodes);
    result.put("edges", edges);
    return result;
  }
}
