package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.UrlMapping;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
  Optional<UrlMapping> findByShortCode(String shortCode);

  Optional<UrlMapping> findByCustomAlias(String customAlias);

  Page<UrlMapping> findAllByUserIdAndIsActiveTrue(Long userId, Pageable pageable);

  boolean existsByShortCode(String shortCode);

  boolean existsByCustomAlias(String customAlias);

  List<UrlMapping> findByExpiresAtBeforeAndIsActiveTrue(OffsetDateTime now);

  @Modifying
  @org.springframework.transaction.annotation.Transactional
  @Query(
      "UPDATE UrlMapping u SET u.clickCount = u.clickCount + :delta WHERE u.shortCode = :shortCode")
  int incrementClickCount(@Param("shortCode") String shortCode, @Param("delta") long delta);

  @Query("SELECT u FROM UrlMapping u WHERE u.user.id = :userId ORDER BY u.createdAt DESC")
  Page<UrlMapping> findAllByUserId(@Param("userId") Long userId, Pageable pageable);
}
