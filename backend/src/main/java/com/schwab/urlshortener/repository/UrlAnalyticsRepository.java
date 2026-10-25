package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.UrlAnalytics;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlAnalyticsRepository extends JpaRepository<UrlAnalytics, Long> {
  @Modifying
  @Query("DELETE FROM UrlAnalytics a WHERE a.clickTimestamp < :cutoff")
  int deleteBefore(@Param("cutoff") OffsetDateTime cutoff);

  long countByShortCode(String shortCode);

  List<UrlAnalytics> findByShortCodeOrderByClickTimestampDesc(String shortCode, Pageable pageable);

  /**
   * Returns aggregated click counts per date within a date range for a given short code. Each
   * element is an Object[] where [0] = LocalDate and [1] = Long count.
   */
  @Query(
      """
SELECT a.clickDate, COUNT(a.id)
FROM UrlAnalytics a
WHERE a.shortCode = :shortCode
AND a.clickDate >= :startDate
AND a.clickDate <= :endDate
GROUP BY a.clickDate
ORDER BY a.clickDate
""")
  List<Object[]> findClicksByDateRange(
      @Param("shortCode") String shortCode,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Returns aggregated click counts per country for a given short code. Each element is an Object[]
   * where [0] = String countryCode and [1] = Long count.
   */
  @Query(
      """
SELECT a.countryCode, COUNT(a.id)
FROM UrlAnalytics a
WHERE a.shortCode = :shortCode
AND a.countryCode IS NOT NULL
GROUP BY a.countryCode
ORDER BY COUNT(a.id) DESC
""")
  List<Object[]> findClicksByCountry(@Param("shortCode") String shortCode);

  /** Returns aggregated click counts per device type for a given short code. */
  @Query(
      """
SELECT a.deviceType, COUNT(a.id)
FROM UrlAnalytics a
WHERE a.shortCode = :shortCode
AND a.deviceType IS NOT NULL
GROUP BY a.deviceType
ORDER BY COUNT(a.id) DESC
""")
  List<Object[]> findClicksByDevice(@Param("shortCode") String shortCode);

  /** Returns aggregated click counts per browser for a given short code. */
  @Query(
      """
SELECT a.browser, COUNT(a.id)
FROM UrlAnalytics a
WHERE a.shortCode = :shortCode
AND a.browser IS NOT NULL
GROUP BY a.browser
ORDER BY COUNT(a.id) DESC
""")
  List<Object[]> findClicksByBrowser(@Param("shortCode") String shortCode);

  /** Returns count of distinct country codes for a given short code. */
  @Query(
      """
SELECT COUNT(DISTINCT a.countryCode)
FROM UrlAnalytics a
WHERE a.shortCode = :shortCode
AND a.countryCode IS NOT NULL
""")
  long countDistinctCountriesByShortCode(@Param("shortCode") String shortCode);
}
