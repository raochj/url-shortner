package com.schwab.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orchestrator_task_logs")
public class OrchestratorTaskLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "task_id", nullable = false, length = 50)
  private String taskId;

  @Column(name = "task_name", nullable = false, length = 200)
  private String taskName;

  @Column(name = "stage", nullable = false, length = 30)
  private String stage;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "attempt_number", nullable = false)
  @Builder.Default
  private int attemptNumber = 1;

  @Column(name = "started_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime completedAt;

  @Column(name = "latency_ms")
  private Long latencyMs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_payload", columnDefinition = "jsonb")
  private String resultPayload;

  @Column(name = "error_detail", columnDefinition = "TEXT")
  private String errorDetail;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "policy_checks", columnDefinition = "jsonb")
  @Builder.Default
  private String policyChecks = "[]";
}
