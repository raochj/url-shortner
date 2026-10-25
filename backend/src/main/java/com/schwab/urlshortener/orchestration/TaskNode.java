package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.model.ApprovalGate;
import com.schwab.urlshortener.orchestration.model.RetryPolicy;
import com.schwab.urlshortener.orchestration.model.TaskResult;
import com.schwab.urlshortener.orchestration.model.TaskStage;
import com.schwab.urlshortener.orchestration.model.TaskStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TaskNode {
  private final String id;
  private final String name;
  private final String description;
  private final TaskStage stage;
  private final List<String> dependencyIds;
  private final Callable<TaskResult> executor;
  private final RollbackHandler rollbackHandler;
  private final RetryPolicy retryPolicy;
  private final ApprovalGate approvalGate;
  private final List<PolicyGuardrail> guardrails;
  // Mutable state
  private final AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.PENDING);
  private final AtomicInteger attemptCount = new AtomicInteger(0);
  private volatile TaskResult lastResult;
  private volatile String blockingPolicyViolation;

  public TaskNode(
      String id,
      String name,
      String description,
      TaskStage stage,
      List<String> dependencyIds,
      Callable<TaskResult> executor,
      RollbackHandler rollbackHandler,
      RetryPolicy retryPolicy,
      ApprovalGate approvalGate,
      List<PolicyGuardrail> guardrails) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.stage = stage;
    this.dependencyIds = Collections.unmodifiableList(new ArrayList<>(dependencyIds));
    this.executor = executor;
    this.rollbackHandler = rollbackHandler;
    this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
    this.approvalGate = approvalGate != null ? approvalGate : ApprovalGate.none();
    this.guardrails = Collections.unmodifiableList(new ArrayList<>(guardrails));
  }

  // Getters
  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public TaskStage getStage() {
    return stage;
  }

  public List<String> getDependencyIds() {
    return dependencyIds;
  }

  public Callable<TaskResult> getExecutor() {
    return executor;
  }

  public RollbackHandler getRollbackHandler() {
    return rollbackHandler;
  }

  public RetryPolicy getRetryPolicy() {
    return retryPolicy;
  }

  public ApprovalGate getApprovalGate() {
    return approvalGate;
  }

  public List<PolicyGuardrail> getGuardrails() {
    return guardrails;
  }

  public TaskStatus getStatus() {
    return status.get();
  }

  public int getAttemptCount() {
    return attemptCount.get();
  }

  public TaskResult getLastResult() {
    return lastResult;
  }

  public String getBlockingPolicyViolation() {
    return blockingPolicyViolation;
  }

  public void setLastResult(TaskResult result) {
    this.lastResult = result;
  }

  public void setBlockingPolicyViolation(String violation) {
    this.blockingPolicyViolation = violation;
  }

  public void transitionTo(TaskStatus newStatus) {
    this.status.set(newStatus);
  }

  public int incrementAttempt() {
    return attemptCount.incrementAndGet();
  }

  public boolean isReadyToExecute(Set<String> completedNodeIds) {
    TaskStatus current = status.get();
    return (current == TaskStatus.PENDING || current == TaskStatus.APPROVED)
        && completedNodeIds.containsAll(dependencyIds);
  }

  // -------------------------------------------------------------------------
  // Builder
  // -------------------------------------------------------------------------
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String id;
    private String name;
    private String description = "";
    private TaskStage stage;
    private final List<String> dependencyIds = new ArrayList<>();
    private Callable<TaskResult> executor;
    private RollbackHandler rollbackHandler;
    private RetryPolicy retryPolicy = RetryPolicy.defaultPolicy();
    private ApprovalGate approvalGate = ApprovalGate.none();
    private final List<PolicyGuardrail> guardrails = new ArrayList<>();

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder stage(TaskStage stage) {
      this.stage = stage;
      return this;
    }

    public Builder dependsOn(String... ids) {
      for (String d : ids) dependencyIds.add(d);
      return this;
    }

    public Builder dependsOn(List<String> ids) {
      dependencyIds.addAll(ids);
      return this;
    }

    public Builder executor(Callable<TaskResult> executor) {
      this.executor = executor;
      return this;
    }

    public Builder rollbackHandler(RollbackHandler handler) {
      this.rollbackHandler = handler;
      return this;
    }

    public Builder retryPolicy(RetryPolicy policy) {
      this.retryPolicy = policy;
      return this;
    }

    public Builder approvalGate(ApprovalGate gate) {
      this.approvalGate = gate;
      return this;
    }

    public Builder guardrail(PolicyGuardrail guardrail) {
      this.guardrails.add(guardrail);
      return this;
    }

    public Builder guardrails(List<PolicyGuardrail> gs) {
      this.guardrails.addAll(gs);
      return this;
    }

    public TaskNode build() {
      if (id == null || id.isBlank()) throw new IllegalStateException("TaskNode id is required");
      if (stage == null) throw new IllegalStateException("TaskNode stage is required");
      if (executor == null) throw new IllegalStateException("TaskNode executor is required");
      return new TaskNode(
          id,
          name != null ? name : id,
          description,
          stage,
          dependencyIds,
          executor,
          rollbackHandler,
          retryPolicy,
          approvalGate,
          guardrails);
    }
  }

  @Override
  public String toString() {
    return "TaskNode{id='" + id + "', stage=" + stage + ", status=" + status.get() + "}";
  }
}
