package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.response.AnalyticsSummaryResponse;
import com.schwab.urlshortener.entity.UrlAnalytics;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.exception.UrlNotFoundException;
import com.schwab.urlshortener.repository.UrlAnalyticsRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
  private final UrlAnalyticsRepository urlAnalyticsRepository;
  private final UrlMappingRepository urlMappingRepository;

  @Value("${app.analytics.retention-days:90}")
  private int retentionDays;

  @Scheduled(cron = "${app.analytics.retention-cron:0 30 2 * * *}")
  @Transactional
  public void deleteExpiredAnalytics() {
    int deleted =
        urlAnalyticsRepository.deleteBefore(OffsetDateTime.now().minusDays(retentionDays));
    if (deleted > 0) log.info("Deleted {} analytics records beyond retention", deleted);
  }

  // =========================================================================
  // Record click (async — does not block the redirect response)
  // =========================================================================
  @Async("analyticsExecutor")
  @Transactional
  public void recordClick(
      String shortCode, String anonymizedIp, String userAgent, String referrer) {
    try {
      UrlAnalytics analytics =
          UrlAnalytics.builder()
              .shortCode(shortCode)
              .clickTimestamp(OffsetDateTime.now())
              .clickDate(LocalDate.now())
              .ipAddress(anonymizedIp)
              .userAgent(userAgent)
              .referrer(referrer)
              .deviceType(detectDeviceType(userAgent))
              .browser(detectBrowser(userAgent))
              .os(detectOs(userAgent))
              .build();
      urlAnalyticsRepository.save(analytics);
      log.debug("Click recorded: shortCode={}", shortCode);
    } catch (Exception e) {
      log.error("Failed to record click for shortCode={}: {}", shortCode, e.getMessage());
    }
  }

  // =========================================================================
  // Summary aggregation
  // =========================================================================
  @Transactional(readOnly = true)
  public AnalyticsSummaryResponse getSummary(String shortCode, String username) {
    verifyOwnership(shortCode, username);
    long totalClicks = urlAnalyticsRepository.countByShortCode(shortCode);
    long uniqueCountries = urlAnalyticsRepository.countDistinctCountriesByShortCode(shortCode);
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(30);
    Map<String, Long> clicksByDate = queryTimeSeries(shortCode, from, to);
    Map<String, Long> clicksByCountry =
        toMap(urlAnalyticsRepository.findClicksByCountry(shortCode));
    Map<String, Long> clicksByDevice = toMap(urlAnalyticsRepository.findClicksByDevice(shortCode));
    Map<String, Long> clicksByBrowser =
        toMap(urlAnalyticsRepository.findClicksByBrowser(shortCode));
    return new AnalyticsSummaryResponse(
        shortCode,
        totalClicks,
        uniqueCountries,
        clicksByDate,
        clicksByCountry,
        clicksByDevice,
        clicksByBrowser);
  }

  // =========================================================================
  // Time series
  // =========================================================================
  @Transactional(readOnly = true)
  public Map<String, Long> getTimeSeries(
      String shortCode, LocalDate from, LocalDate to, String username) {
    verifyOwnership(shortCode, username);
    return queryTimeSeries(shortCode, from, to);
  }

  private Map<String, Long> queryTimeSeries(String shortCode, LocalDate from, LocalDate to) {
    List<Object[]> rows = urlAnalyticsRepository.findClicksByDateRange(shortCode, from, to);
    Map<String, Long> result = new LinkedHashMap<>();
    for (Object[] row : rows) {
      LocalDate date = (LocalDate) row[0];
      Long count = (Long) row[1];
      result.put(date.toString(), count);
    }
    return result;
  }

  // =========================================================================
  // Helpers
  // =========================================================================
  private Map<String, Long> toMap(List<Object[]> rows) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (Object[] row : rows) {
      if (row[0] != null) {
        result.put(row[0].toString(), (Long) row[1]);
      }
    }
    return result;
  }

  private void verifyOwnership(String shortCode, String username) {
    UrlMapping mapping =
        urlMappingRepository
            .findByShortCode(shortCode)
            .filter(UrlMapping::isActive)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
    if (mapping.getUser() != null && !mapping.getUser().getUsername().equals(username)) {
      throw new AccessDeniedException("You do not own the URL with short code: " + shortCode);
    }
  }

  private String detectDeviceType(String userAgent) {
    if (userAgent == null) return "UNKNOWN";
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "MOBILE";
    if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
    return "DESKTOP";
  }

  private String detectBrowser(String userAgent) {
    if (userAgent == null) return "UNKNOWN";
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("edg/")) return "Edge";
    if (ua.contains("chrome/")) return "Chrome";
    if (ua.contains("firefox/")) return "Firefox";
    if (ua.contains("safari/") && !ua.contains("chrome")) return "Safari";
    if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
    return "OTHER";
  }

  private String detectOs(String userAgent) {
    if (userAgent == null) return "UNKNOWN";
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("windows")) return "Windows";
    if (ua.contains("mac os")) return "macOS";
    if (ua.contains("android")) return "Android";
    if (ua.contains("ios") || ua.contains("iphone") || ua.contains("ipad")) return "iOS";
    if (ua.contains("linux")) return "Linux";
    return "OTHER";
  }
}
