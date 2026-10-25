package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.response.AnalyticsSummaryResponse;
import com.schwab.urlshortener.dto.response.ApiResponse;
import com.schwab.urlshortener.service.AnalyticsService;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
  private final AnalyticsService analyticsService;

  @GetMapping("/{shortCode}/summary")
  public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> getSummary(
      @PathVariable String shortCode, @AuthenticationPrincipal UserDetails userDetails) {
    AnalyticsSummaryResponse summary =
        analyticsService.getSummary(shortCode, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(summary));
  }

  @GetMapping("/{shortCode}/timeseries")
  public ResponseEntity<ApiResponse<Map<String, Long>>> getTimeSeries(
      @PathVariable String shortCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal UserDetails userDetails) {
    LocalDate effectiveTo = to != null ? to : LocalDate.now();
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new IllegalArgumentException("Analytics 'from' date must not be after 'to' date");
    }
    if (effectiveFrom.isBefore(effectiveTo.minusDays(366))) {
      throw new IllegalArgumentException("Analytics range must not exceed 366 days");
    }
    Map<String, Long> series =
        analyticsService.getTimeSeries(
            shortCode, effectiveFrom, effectiveTo, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(series));
  }
}
