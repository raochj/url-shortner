package com.schwab.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orchestrator_runs")
public class OrchestratorRun {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Version private long version;

  @Column(name = "scenario_type", nullable = false, length = 20)
  private String scenarioType;

  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private String status = "PENDING";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameters", columnDefinition = "jsonb")
  @Builder.Default
  private String parameters = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dag_snapshot", columnDefinition = "jsonb")
  private String dagSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metrics_snapshot", columnDefinition = "jsonb")
  private String metricsSnapshot;

  @Column(name = "started_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime completedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "initiated_by")
  private User initiatedBy;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;
}
