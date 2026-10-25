package com.schwab.urlshortener.service;

import com.schwab.urlshortener.repository.UrlMappingRepository;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {
  private static final String URL_PREFIX = "url:";
  private static final String CLICKS_PREFIX = "clicks:";
  private final RedisTemplate<String, String> redisTemplate;
  private final UrlMappingRepository urlMappingRepository;

  // =========================================================================
  // URL cache operations
  // =========================================================================
  public Optional<String> getUrl(String shortCode) {
    try {
      String value = redisTemplate.opsForValue().get(URL_PREFIX + shortCode);
      return Optional.ofNullable(value);
    } catch (Exception e) {
      log.warn("Redis getUrl failed for shortCode={}: {}", shortCode, e.getMessage());
      return Optional.empty();
    }
  }

  public void cacheUrl(String shortCode, String longUrl, long ttlSeconds) {
    try {
      redisTemplate
          .opsForValue()
          .set(URL_PREFIX + shortCode, longUrl, ttlSeconds, TimeUnit.SECONDS);
      log.debug("Cached URL: shortCode={} ttl={}s", shortCode, ttlSeconds);
    } catch (Exception e) {
      log.warn("Redis cacheUrl failed for shortCode={}: {}", shortCode, e.getMessage());
    }
  }

  public void evictUrl(String shortCode) {
    try {
      redisTemplate.delete(URL_PREFIX + shortCode);
      log.debug("Evicted URL cache for shortCode={}", shortCode);
    } catch (Exception e) {
      log.warn("Redis evictUrl failed for shortCode={}: {}", shortCode, e.getMessage());
    }
  }

  // =========================================================================
  // Click count operations
  // =========================================================================
  public void incrementClickCount(String shortCode) {
    try {
      redisTemplate.opsForValue().increment(CLICKS_PREFIX + shortCode);
    } catch (Exception e) {
      log.warn("Redis incrementClickCount failed for shortCode={}: {}", shortCode, e.getMessage());
    }
  }

  public Long getClickCount(String shortCode) {
    try {
      String value = redisTemplate.opsForValue().get(CLICKS_PREFIX + shortCode);
      return value != null ? Long.parseLong(value) : 0L;
    } catch (Exception e) {
      log.warn("Redis getClickCount failed for shortCode={}: {}", shortCode, e.getMessage());
      return 0L;
    }
  }

  // =========================================================================
  // Scheduled click count sync
  // =========================================================================
  /**
   * Periodically flushes Redis click counters into the PostgreSQL url_mappings table. Reads all
   * "clicks:" keys, atomically claims each delta with GETDEL, and increments PostgreSQL. A delta is
   * restored when persistence fails and discarded when its URL has already been deleted.
   */
  @Scheduled(fixedDelayString = "${app.cache.click-sync-interval-ms:300000}")
  public void syncClickCounts() {
    log.debug("Starting click count sync from Redis to DB...");
    try {
      int synced = 0;
      try (Cursor<String> keys =
          redisTemplate.scan(
              ScanOptions.scanOptions().match(CLICKS_PREFIX + "*").count(100).build())) {
        while (keys.hasNext()) {
          String key = keys.next();
          long delta = 0L;
          try {
            String value = redisTemplate.opsForValue().getAndDelete(key);
            if (value == null) continue;
            delta = Long.parseLong(value);
            if (delta <= 0) continue;
            String shortCode = key.substring(CLICKS_PREFIX.length());
            int updated = urlMappingRepository.incrementClickCount(shortCode, delta);
            if (updated > 0) {
              synced++;
              log.debug("Synced {} clicks for shortCode={}", delta, shortCode);
            } else {
              log.info("Discarding orphaned click counter for deleted shortCode={}", shortCode);
            }
          } catch (NumberFormatException e) {
            log.warn("Invalid click count value for key={}: {}", key, e.getMessage());
          } catch (RuntimeException e) {
            log.error("Failed to sync click count for key={}: {}", key, e.getMessage());
            if (delta > 0) {
              try {
                redisTemplate.opsForValue().increment(key, delta);
              } catch (RuntimeException restoreError) {
                log.error(
                    "Failed to restore {} clicks for key={}: {}",
                    delta,
                    key,
                    restoreError.getMessage());
              }
            }
          }
        }
      }
      if (synced > 0) {
        log.info("Click count sync complete: {} short codes updated", synced);
      }
    } catch (RuntimeException e) {
      log.error("Click count sync failed", e);
    }
  }
}
