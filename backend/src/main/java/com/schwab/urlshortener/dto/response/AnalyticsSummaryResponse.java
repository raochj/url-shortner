package com.schwab.urlshortener.dto.response;

import java.util.Map;

public record AnalyticsSummaryResponse(
    String shortCode,
    long totalClicks,
    long uniqueCountries,
    Map<String, Long> clicksByDate,
    Map<String, Long> clicksByCountry,
    Map<String, Long> clicksByDevice,
    Map<String, Long> clicksByBrowser) {
  public AnalyticsSummaryResponse {
    clicksByDate = Map.copyOf(clicksByDate);
    clicksByCountry = Map.copyOf(clicksByCountry);
    clicksByDevice = Map.copyOf(clicksByDevice);
    clicksByBrowser = Map.copyOf(clicksByBrowser);
  }
}
