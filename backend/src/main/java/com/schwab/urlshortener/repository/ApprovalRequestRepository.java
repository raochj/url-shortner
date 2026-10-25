package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.ApprovalRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM ApprovalRequest a WHERE a.id = :id")
  Optional<ApprovalRequest> findByIdForUpdate(@Param("id") UUID id);

  Optional<ApprovalRequest> findByRunIdAndTaskIdAndStatus(UUID runId, String taskId, String status);

  List<ApprovalRequest> findAllByRunIdAndStatus(UUID runId, String status);

  List<ApprovalRequest> findAllByRunId(UUID runId);
}
