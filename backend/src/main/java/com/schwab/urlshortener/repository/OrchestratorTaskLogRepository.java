package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.OrchestratorTaskLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrchestratorTaskLogRepository extends JpaRepository<OrchestratorTaskLog, Long> {
  List<OrchestratorTaskLog> findAllByRunIdOrderByStartedAt(UUID runId);

  List<OrchestratorTaskLog> findAllByRunIdAndTaskId(UUID runId, String taskId);
}
