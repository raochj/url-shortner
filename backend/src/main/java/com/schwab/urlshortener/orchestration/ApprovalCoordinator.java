package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.entity.ApprovalRequest;
import com.schwab.urlshortener.repository.ApprovalRequestRepository;
import com.schwab.urlshortener.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCoordinator {
  private final ApprovalRequestRepository approvalRequestRepository;
  private final UserRepository userRepository;

  @Value("${app.orchestration.approval-timeout-minutes:10}")
  private long approvalTimeoutMinutes = 10;

  /**
   * Creates a new PENDING approval request for the given run/task combination. If one already
   * exists with PENDING status, returns that instead of creating a duplicate.
   */
  @Transactional
  public ApprovalRequest createApproval(String runId, String taskId, String prompt) {
    UUID runUuid = UUID.fromString(runId);
    // Idempotent: return existing pending approval if present
    Optional<ApprovalRequest> existing =
        approvalRequestRepository.findByRunIdAndTaskIdAndStatus(runUuid, taskId, "PENDING");
    if (existing.isPresent()) {
      log.debug("Returning existing PENDING approval for run={} task={}", runId, taskId);
      return existing.get();
    }
    ApprovalRequest request =
        ApprovalRequest.builder()
            .runId(runUuid)
            .taskId(taskId)
            .prompt(prompt)
            .status("PENDING")
            .expiresAt(OffsetDateTime.now().plusMinutes(approvalTimeoutMinutes))
            .build();
    ApprovalRequest saved = approvalRequestRepository.save(request);
    log.info("Created approval request id={} for run={} task={}", saved.getId(), runId, taskId);
    return saved;
  }

  /**
   * Processes an approval decision (approve or reject). Updates the ApprovalRequest status, records
   * the deciding user and timestamp.
   */
  @Transactional
  public ApprovalRequest processDecision(
      UUID approvalId, boolean approved, Long approverId, String comment) {
    ApprovalRequest request =
        approvalRequestRepository
            .findByIdForUpdate(approvalId)
            .orElseThrow(
                () -> new IllegalArgumentException("ApprovalRequest not found: " + approvalId));
    if (!"PENDING".equals(request.getStatus())) {
      throw new IllegalStateException(
          "ApprovalRequest " + approvalId + " is already in status: " + request.getStatus());
    }
    request.setStatus(approved ? "APPROVED" : "REJECTED");
    request.setDecidedAt(OffsetDateTime.now());
    request.setApproverComment(comment);
    if (approverId != null) {
      userRepository.findById(approverId).ifPresent(request::setApprover);
    }
    ApprovalRequest saved = approvalRequestRepository.save(request);
    log.info(
        "Approval {} processed: decision={} by approver={}",
        approvalId,
        saved.getStatus(),
        approverId);
    return saved;
  }

  /** Retrieves the most recent pending approval for a given run and task. */
  @Transactional(readOnly = true)
  public Optional<ApprovalRequest> getPendingApproval(UUID runId, String taskId) {
    return approvalRequestRepository.findByRunIdAndTaskIdAndStatus(runId, taskId, "PENDING");
  }

  /** Returns true if an APPROVED approval exists for the given run/task. */
  @Transactional(readOnly = true)
  public boolean isApproved(UUID runId, String taskId) {
    return approvalRequestRepository
        .findByRunIdAndTaskIdAndStatus(runId, taskId, "APPROVED")
        .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean isRejected(UUID runId, String taskId) {
    return approvalRequestRepository
        .findByRunIdAndTaskIdAndStatus(runId, taskId, "REJECTED")
        .isPresent();
  }

  @Transactional
  public void expire(ApprovalRequest request) {
    if (request.isPending()) {
      request.setStatus("EXPIRED");
      request.setDecidedAt(OffsetDateTime.now());
      approvalRequestRepository.save(request);
    }
  }

  /** Returns true if the given approval request has expired (expiresAt is in the past). */
  public boolean isExpired(ApprovalRequest request) {
    return request.isExpired();
  }

  /** Count of pending approvals for a run. */
  @Transactional(readOnly = true)
  public int countPending(UUID runId) {
    return approvalRequestRepository.findAllByRunIdAndStatus(runId, "PENDING").size();
  }

  /** Count of granted (APPROVED) approvals for a run. */
  @Transactional(readOnly = true)
  public int countApproved(UUID runId) {
    return approvalRequestRepository.findAllByRunIdAndStatus(runId, "APPROVED").size();
  }
}
