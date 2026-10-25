package com.schwab.urlshortener.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUrlRequest(
    @Size(max = 4096, message = "Long URL must not exceed 4096 characters") String longUrl,
    Boolean isActive,
    @Min(value = 0, message = "Expiration cannot be negative")
        @Max(value = 3650, message = "Expiration must not exceed 3650 days")
        Integer expirationDays,
    @Size(max = 20, message = "At most 20 tags are allowed") List<@Size(max = 50) String> tags) {
  public UpdateUrlRequest {
    if (tags != null) tags = List.copyOf(tags);
  }

  @Override
  public List<String> tags() {
    return tags == null ? null : List.copyOf(tags);
  }
}
