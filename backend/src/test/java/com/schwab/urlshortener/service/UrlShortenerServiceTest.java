package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.dto.request.ShortenUrlRequest;
import com.schwab.urlshortener.dto.response.ShortenUrlResponse;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.exception.DuplicateAliasException;
import com.schwab.urlshortener.exception.PolicyViolationException;
import com.schwab.urlshortener.exception.UrlExpiredException;
import com.schwab.urlshortener.exception.UrlNotFoundException;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.repository.UserRepository;
import com.schwab.urlshortener.util.ShortCodeGenerator;
import com.schwab.urlshortener.util.UrlValidator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlShortenerService unit tests")
class UrlShortenerServiceTest {
  @Mock private UrlMappingRepository urlMappingRepository;
  @Mock private UserRepository userRepository;
  @Mock private ShortCodeGenerator shortCodeGenerator;
  @Mock private CacheService cacheService;
  @Mock private UrlValidator urlValidator;
  @InjectMocks private UrlShortenerService urlShortenerService;
  private static final String USERNAME = "testuser";
  private static final String LONG_URL = "https://www.schwab.com/some/very/long/page";
  private static final String SHORT_CODE = "abc123";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(urlShortenerService, "baseUrl", "http://localhost:8080");
    ReflectionTestUtils.setField(urlShortenerService, "urlTtlSeconds", 604800L);
  }

  // =========================================================================
  // shorten
  // =========================================================================
  @Test
  @DisplayName("shorten_validUrl_returnsShortCode")
  void shorten_validUrl_returnsShortCode() {
    // Arrange
    User user =
        User.builder().id(1L).username(USERNAME).email("test@example.com").role("USER").build();
    UrlMapping savedMapping =
        UrlMapping.builder()
            .id(1L)
            .shortCode(SHORT_CODE)
            .longUrl(LONG_URL)
            .user(user)
            .isActive(true)
            .clickCount(0L)
            .tags(List.of())
            .build();
    when(urlValidator.isValid(LONG_URL)).thenReturn(true);
    when(shortCodeGenerator.generate()).thenReturn(SHORT_CODE);
    when(urlMappingRepository.existsByShortCode(SHORT_CODE)).thenReturn(false);
    when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(savedMapping);
    ShortenUrlRequest request = new ShortenUrlRequest(LONG_URL, null, null, null);
    // Act
    ShortenUrlResponse response = urlShortenerService.shorten(request, USERNAME);
    // Assert
    assertThat(response).isNotNull();
    assertThat(response.shortCode()).isEqualTo(SHORT_CODE);
    assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/" + SHORT_CODE);
    assertThat(response.longUrl()).isEqualTo(LONG_URL);
    verify(cacheService).cacheUrl(SHORT_CODE, LONG_URL, 604800L);
  }

  @Test
  @DisplayName("shorten_ssrfUrl_throwsPolicyViolationException")
  void shorten_ssrfUrl_throwsPolicyViolationException() {
    // Arrange — private IP address
    String ssrfUrl = "http://192.168.1.1/admin";
    when(urlValidator.isValid(ssrfUrl)).thenReturn(false);
    ShortenUrlRequest request = new ShortenUrlRequest(ssrfUrl, null, null, null);
    // Act & Assert
    assertThatThrownBy(() -> urlShortenerService.shorten(request, USERNAME))
        .isInstanceOf(PolicyViolationException.class)
        .hasMessageContaining("SSRF");
    verify(urlMappingRepository, never()).save(any());
    verify(cacheService, never()).cacheUrl(anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("shorten_customAlias_duplicateAlias_throwsDuplicateAliasException")
  void shorten_customAlias_duplicateAlias_throwsDuplicateAliasException() {
    // Arrange
    String duplicateAlias = "mylink";
    when(urlValidator.isValid(LONG_URL)).thenReturn(true);
    when(urlMappingRepository.existsByCustomAlias(duplicateAlias)).thenReturn(true);
    ShortenUrlRequest request = new ShortenUrlRequest(LONG_URL, duplicateAlias, null, null);
    // Act & Assert
    assertThatThrownBy(() -> urlShortenerService.shorten(request, USERNAME))
        .isInstanceOf(DuplicateAliasException.class)
        .hasMessageContaining(duplicateAlias);
    verify(urlMappingRepository, never()).save(any());
  }

  // =========================================================================
  // resolveForRedirect
  // =========================================================================
  @Test
  @DisplayName("resolveForRedirect_activeUrl_returnsLongUrl (cache hit path)")
  void resolveForRedirect_activeUrl_returnsLongUrl_cacheHit() {
    // Arrange — cache hit
    when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.of(LONG_URL));
    // Act
    String resolved = urlShortenerService.resolveForRedirect(SHORT_CODE);
    // Assert
    assertThat(resolved).isEqualTo(LONG_URL);
    // DB should not be queried when cache hits
    verify(urlMappingRepository, never()).findByShortCode(anyString());
  }

  @Test
  @DisplayName("resolveForRedirect_expiredUrl_throwsUrlExpiredException")
  void resolveForRedirect_expiredUrl_throwsUrlExpiredException() {
    // Arrange — cache miss, DB returns expired mapping
    when(cacheService.getUrl(SHORT_CODE)).thenReturn(Optional.empty());
    UrlMapping expiredMapping =
        UrlMapping.builder()
            .shortCode(SHORT_CODE)
            .longUrl(LONG_URL)
            .isActive(true)
            .expiresAt(OffsetDateTime.now().minusDays(1)) // expired yesterday
            .clickCount(0L)
            .tags(List.of())
            .build();
    when(urlMappingRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(expiredMapping));
    // Act & Assert
    assertThatThrownBy(() -> urlShortenerService.resolveForRedirect(SHORT_CODE))
        .isInstanceOf(UrlExpiredException.class)
        .hasMessageContaining(SHORT_CODE);
    verify(cacheService, never()).cacheUrl(anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("resolveForRedirect_unknownShortCode_throwsUrlNotFoundException")
  void resolveForRedirect_unknownShortCode_throwsUrlNotFoundException() {
    // Arrange
    when(cacheService.getUrl("unknown")).thenReturn(Optional.empty());
    when(urlMappingRepository.findByShortCode("unknown")).thenReturn(Optional.empty());
    // Act & Assert
    assertThatThrownBy(() -> urlShortenerService.resolveForRedirect("unknown"))
        .isInstanceOf(UrlNotFoundException.class);
  }
}
