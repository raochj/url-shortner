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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "task_id", nullable = false, length = 50)
  private String taskId;

  @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private String status = "PENDING";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approver_id")
  private User approver;

  @Column(name = "approver_comment", columnDefinition = "TEXT")
  private String approverComment;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  @Column(name = "decided_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime decidedAt;

  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  @Builder.Default
  private OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);

  public boolean isExpired() {
    return OffsetDateTime.now().isAfter(expiresAt);
  }

  public boolean isPending() {
    return "PENDING".equals(status);
  }
}
