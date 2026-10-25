package com.schwab.urlshortener.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ShortenUrlResponse(
    String shortCode,
    String shortUrl,
    String longUrl,
    String customAlias,
    boolean isActive,
    OffsetDateTime expiresAt,
    long clickCount,
    List<String> tags,
    OffsetDateTime createdAt) {
  public ShortenUrlResponse {
    tags = List.copyOf(tags);
  }
}
