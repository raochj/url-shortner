package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.util.IpAddressAnonymizer;
import com.schwab.urlshortener.util.IpAddressExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {
  @Mock private UrlShortenerService urlShortenerService;
  @Mock private AnalyticsService analyticsService;
  @Mock private CacheService cacheService;
  @Mock private IpAddressExtractor ipAddressExtractor;
  @Mock private IpAddressAnonymizer ipAddressAnonymizer;
  @Mock private HttpServletRequest request;
  @InjectMocks private RedirectService redirectService;

  @Test
  void redirectCapturesImmutableAnalyticsAndIncrementsCounter() {
    String longUserAgent = "x".repeat(600);
    when(urlShortenerService.resolveForRedirect("abc12345")).thenReturn("https://example.com/path");
    when(ipAddressExtractor.extract(request)).thenReturn("203.0.113.8");
    when(ipAddressAnonymizer.anonymize("203.0.113.8")).thenReturn("pseudonym");
    when(request.getHeader("User-Agent")).thenReturn(longUserAgent);
    when(request.getHeader("Referer")).thenReturn("https://referrer.example");

    var response = redirectService.redirect("abc12345", request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
        .isEqualTo("https://example.com/path");
    verify(cacheService).incrementClickCount("abc12345");
    verify(analyticsService)
        .recordClick("abc12345", "pseudonym", "x".repeat(512), "https://referrer.example");
  }

  @Test
  void analyticsBackpressureDoesNotFailRedirect() {
    when(urlShortenerService.resolveForRedirect("abc12345")).thenReturn("https://example.com/path");
    when(ipAddressExtractor.extract(request)).thenReturn("203.0.113.8");
    when(ipAddressAnonymizer.anonymize("203.0.113.8")).thenReturn("pseudonym");
    doThrow(new IllegalStateException("queue full"))
        .when(analyticsService)
        .recordClick("abc12345", "pseudonym", null, null);

    var response = redirectService.redirect("abc12345", request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    verify(cacheService).incrementClickCount("abc12345");
  }
}
