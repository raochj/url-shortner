package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.request.ShortenUrlRequest;
import com.schwab.urlshortener.dto.request.UpdateUrlRequest;
import com.schwab.urlshortener.dto.response.ShortenUrlResponse;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.exception.CapacityExceededException;
import com.schwab.urlshortener.exception.DuplicateAliasException;
import com.schwab.urlshortener.exception.PolicyViolationException;
import com.schwab.urlshortener.exception.UrlExpiredException;
import com.schwab.urlshortener.exception.UrlNotFoundException;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.repository.UserRepository;
import com.schwab.urlshortener.util.ShortCodeGenerator;
import com.schwab.urlshortener.util.UrlValidator;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UrlShortenerService {
  private final UrlMappingRepository urlMappingRepository;
  private final UserRepository userRepository;
  private final ShortCodeGenerator shortCodeGenerator;
  private final CacheService cacheService;
  private final UrlValidator urlValidator;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  @Value("${app.cache.url-ttl-seconds:604800}")
  private long urlTtlSeconds;

  // =========================================================================
  // Shorten
  // =========================================================================
  public ShortenUrlResponse shorten(ShortenUrlRequest request, String username) {
    // Validate URL (SSRF guard)
    if (!urlValidator.isValid(request.longUrl())) {
      throw new PolicyViolationException(
          "SSRF",
          "The URL '"
              + request.longUrl()
              + "' is not allowed (private/loopback address or invalid format)");
    }
    // Determine short code
    String shortCode;
    if (request.customAlias() != null && !request.customAlias().isBlank()) {
      String alias = request.customAlias().trim();
      if (urlMappingRepository.existsByCustomAlias(alias)
          || urlMappingRepository.existsByShortCode(alias)) {
        throw new DuplicateAliasException(alias, "custom alias");
      }
      shortCode = alias;
    } else {
      shortCode = generateUniqueShortCode();
    }
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    OffsetDateTime expiresAt = null;
    if (request.expirationDays() != null && request.expirationDays() > 0) {
      expiresAt = OffsetDateTime.now().plusDays(request.expirationDays());
    }
    UrlMapping mapping =
        UrlMapping.builder()
            .shortCode(shortCode)
            .longUrl(request.longUrl().trim())
            .user(user)
            .customAlias(
                request.customAlias() != null && !request.customAlias().isBlank()
                    ? request.customAlias().trim()
                    : null)
            .isActive(true)
            .expiresAt(expiresAt)
            .clickCount(0L)
            .tags(sanitizeTags(request.tags()))
            .build();
    urlMappingRepository.save(mapping);
    cacheMapping(mapping);
    log.info("URL shortened: shortCode={} user={}", shortCode, username);
    return toResponse(mapping);
  }

  private String generateUniqueShortCode() {
    for (int attempt = 0; attempt < 10; attempt++) {
      String code = shortCodeGenerator.generate();
      if (!urlMappingRepository.existsByShortCode(code)) return code;
    }
    throw new CapacityExceededException("Unable to allocate a unique short code; retry later");
  }

  // =========================================================================
  // List user URLs
  // =========================================================================
  @Transactional(readOnly = true)
  public Page<ShortenUrlResponse> getUserUrls(String username, Pageable pageable) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return urlMappingRepository
        .findAllByUserIdAndIsActiveTrue(user.getId(), pageable)
        .map(this::toResponse);
  }

  // =========================================================================
  // Get by short code
  // =========================================================================
  @Transactional(readOnly = true)
  public ShortenUrlResponse getByShortCode(String shortCode, String username) {
    UrlMapping mapping = findActiveMapping(shortCode);
    verifyOwnership(mapping, username);
    return toResponse(mapping);
  }

  // =========================================================================
  // Update
  // =========================================================================
  public ShortenUrlResponse update(String shortCode, UpdateUrlRequest request, String username) {
    UrlMapping mapping = findActiveMapping(shortCode);
    verifyOwnership(mapping, username);
    if (request.longUrl() != null && !request.longUrl().isBlank()) {
      if (!urlValidator.isValid(request.longUrl())) {
        throw new PolicyViolationException(
            "SSRF", "The URL '" + request.longUrl() + "' is not allowed");
      }
      mapping.setLongUrl(request.longUrl().trim());
    }
    if (request.isActive() != null) {
      mapping.setActive(request.isActive());
    }
    if (request.expirationDays() != null) {
      mapping.setExpiresAt(
          request.expirationDays() > 0
              ? OffsetDateTime.now().plusDays(request.expirationDays())
              : null);
    }
    if (request.tags() != null) {
      mapping.setTags(sanitizeTags(request.tags()));
    }
    urlMappingRepository.save(mapping);
    cacheService.evictUrl(shortCode);
    if (mapping.isActive()) {
      cacheMapping(mapping);
    }
    log.info("URL updated: shortCode={}", shortCode);
    return toResponse(mapping);
  }

  // =========================================================================
  // Delete (soft)
  // =========================================================================
  public void delete(String shortCode, String username) {
    UrlMapping mapping = findActiveMapping(shortCode);
    verifyOwnership(mapping, username);
    mapping.setActive(false);
    urlMappingRepository.save(mapping);
    cacheService.evictUrl(shortCode);
    log.info("URL soft-deleted: shortCode={}", shortCode);
  }

  // =========================================================================
  // Resolve for redirect
  // =========================================================================
  @Transactional(readOnly = true)
  public String resolveForRedirect(String shortCode) {
    // 1. Check Redis cache first
    return cacheService
        .getUrl(shortCode)
        .orElseGet(
            () -> {
              // 2. Fall back to DB
              UrlMapping mapping =
                  urlMappingRepository
                      .findByShortCode(shortCode)
                      .orElseThrow(() -> new UrlNotFoundException(shortCode));
              if (!mapping.isActive()) {
                throw new UrlNotFoundException(shortCode);
              }
              if (mapping.isExpired()) {
                throw new UrlExpiredException(
                    shortCode,
                    mapping.getExpiresAt() != null ? mapping.getExpiresAt().toString() : "unknown");
              }
              // Repopulate cache for future hits
              cacheMapping(mapping);
              return mapping.getLongUrl();
            });
  }

  // =========================================================================
  // Helpers
  // =========================================================================
  private UrlMapping findActiveMapping(String shortCode) {
    return urlMappingRepository
        .findByShortCode(shortCode)
        .filter(UrlMapping::isActive)
        .orElseThrow(() -> new UrlNotFoundException(shortCode));
  }

  private void verifyOwnership(UrlMapping mapping, String username) {
    if (mapping.getUser() == null) return;
    if (!mapping.getUser().getUsername().equals(username)) {
      throw new AccessDeniedException(
          "You do not own the URL with short code: " + mapping.getShortCode());
    }
  }

  private void cacheMapping(UrlMapping mapping) {
    long ttl = urlTtlSeconds;
    if (mapping.getExpiresAt() != null) {
      long untilExpiration =
          Duration.between(OffsetDateTime.now(), mapping.getExpiresAt()).getSeconds();
      if (untilExpiration <= 0) {
        cacheService.evictUrl(mapping.getShortCode());
        return;
      }
      ttl = Math.min(ttl, untilExpiration);
    }
    cacheService.cacheUrl(mapping.getShortCode(), mapping.getLongUrl(), Math.max(1L, ttl));
  }

  private java.util.List<String> sanitizeTags(java.util.List<String> tags) {
    if (tags == null) return java.util.List.of();
    return tags.stream()
        .filter(java.util.Objects::nonNull)
        .map(String::trim)
        .filter(tag -> !tag.isBlank())
        .distinct()
        .toList();
  }

  private ShortenUrlResponse toResponse(UrlMapping mapping) {
    String shortUrl = baseUrl + "/" + mapping.getShortCode();
    return new ShortenUrlResponse(
        mapping.getShortCode(),
        shortUrl,
        mapping.getLongUrl(),
        mapping.getCustomAlias(),
        mapping.isActive(),
        mapping.getExpiresAt(),
        mapping.getClickCount(),
        mapping.getTags(),
        mapping.getCreatedAt());
  }
}
