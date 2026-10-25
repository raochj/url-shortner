package com.schwab.urlshortener.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ShortenUrlRequest(
    @NotBlank(message = "Long URL is required")
        @Size(max = 4096, message = "Long URL must not exceed 4096 characters")
        String longUrl,
    @Pattern(
            regexp = "^[A-Za-z0-9]{4,10}$",
            message = "Custom alias must contain 4-10 alphanumeric characters")
        String customAlias,
    @Min(value = 1, message = "Expiration must be at least one day")
        @Max(value = 3650, message = "Expiration must not exceed 3650 days")
        Integer expirationDays,
    @Size(max = 20, message = "At most 20 tags are allowed") List<@Size(max = 50) String> tags) {
  public ShortenUrlRequest {
    if (tags != null) tags = List.copyOf(tags);
  }

  @Override
  public List<String> tags() {
    return tags == null ? null : List.copyOf(tags);
  }
}
