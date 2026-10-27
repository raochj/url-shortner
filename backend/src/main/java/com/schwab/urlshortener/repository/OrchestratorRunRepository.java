package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.OrchestratorRun;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, UUID> {
  Page<OrchestratorRun> findAllByInitiatedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Page<OrchestratorRun> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  Page<OrchestratorRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

  List<OrchestratorRun> findAllByStatusIn(Collection<String> statuses);
}
