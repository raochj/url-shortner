package com.schwab.urlshortener.service;

import com.schwab.urlshortener.util.IpAddressAnonymizer;
import com.schwab.urlshortener.util.IpAddressExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {
  private final UrlShortenerService urlShortenerService;
  private final AnalyticsService analyticsService;
  private final CacheService cacheService;
  private final IpAddressExtractor ipAddressExtractor;
  private final IpAddressAnonymizer ipAddressAnonymizer;

  /**
   * Resolves the short code to a long URL and builds a 302 redirect response. Click analytics are
   * recorded asynchronously so that they do not add latency to the redirect path.
   *
   * @param shortCode the 4-10 character short code extracted from the URL
   * @param request the original HTTP request (used for analytics)
   * @return a 302 Found response with the Location header set to the long URL
   */
  public ResponseEntity<Void> redirect(String shortCode, HttpServletRequest request) {
    // resolveForRedirect throws UrlNotFoundException / UrlExpiredException on failure
    String longUrl = urlShortenerService.resolveForRedirect(shortCode);
    cacheService.incrementClickCount(shortCode);
    // Fire-and-forget analytics recording (does not affect redirect latency)
    try {
      // Servlet request objects are container-owned and must not cross the async boundary.
      analyticsService.recordClick(
          shortCode,
          ipAddressAnonymizer.anonymize(ipAddressExtractor.extract(request)),
          truncate(request.getHeader("User-Agent"), 512),
          truncate(request.getHeader("Referer"), 2048));
    } catch (RuntimeException ex) {
      log.warn("Analytics queue unavailable for shortCode={}: {}", shortCode, ex.getMessage());
    }
    log.debug("Redirect resolved for shortCode={}", shortCode);
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, longUrl).build();
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }
}
