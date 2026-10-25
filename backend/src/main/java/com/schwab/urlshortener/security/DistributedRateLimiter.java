package com.schwab.urlshortener.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Distributed fixed-window limiter used for abuse-sensitive mutation endpoints. */
@Slf4j
@Service
public class DistributedRateLimiter {
  private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
      new DefaultRedisScript<>(
          """
          local current = redis.call('INCR', KEYS[1])
          if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
          end
          return current
          """,
          Long.class);

  private final RedisTemplate<String, String> redisTemplate;
  private final Counter rejectedCounter;
  private final Counter degradedCounter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RedisTemplate is a thread-safe, container-managed collaborator.")
  public DistributedRateLimiter(
      @Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate,
      MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.rejectedCounter = meterRegistry.counter("urlshortener.rate_limit.rejected");
    this.degradedCounter = meterRegistry.counter("urlshortener.rate_limit.degraded");
  }

  public Decision acquire(String policy, String subject, int limit, long windowSeconds) {
    String key = "rate:" + policy + ":" + sha256(subject);
    try {
      Long count =
          redisTemplate.execute(
              INCREMENT_SCRIPT,
              List.of(key),
              Long.toString(windowSeconds),
              Integer.toString(limit));
      long used = count != null ? count : 0L;
      boolean allowed = used <= limit;
      if (!allowed) rejectedCounter.increment();
      return new Decision(allowed, Math.max(0L, limit - used), windowSeconds, false);
    } catch (RuntimeException ex) {
      // Availability wins for this application; ingress/WAF limits remain the production backstop.
      degradedCounter.increment();
      log.warn("Rate limiter unavailable for policy={}: {}", policy, ex.getMessage());
      return new Decision(true, limit, windowSeconds, true);
    }
  }

  private String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  public record Decision(
      boolean allowed, long remaining, long retryAfterSeconds, boolean degraded) {}
}
