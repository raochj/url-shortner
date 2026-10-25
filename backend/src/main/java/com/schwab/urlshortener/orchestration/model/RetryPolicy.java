package com.schwab.urlshortener.orchestration.model;

import java.time.Duration;

public record RetryPolicy(
    int maxAttempts, Duration initialDelay, double backoffMultiplier, Duration maxDelay) {
  public static RetryPolicy defaultPolicy() {
    return new RetryPolicy(3, Duration.ofMillis(500), 2.0, Duration.ofSeconds(10));
  }

  public static RetryPolicy noRetry() {
    return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
  }

  public Duration getDelayForAttempt(int attempt) {
    long ms = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
    return Duration.ofMillis(Math.min(ms, maxDelay.toMillis()));
  }
}
