package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.request.ShortenUrlRequest;
import com.schwab.urlshortener.dto.request.UpdateUrlRequest;
import com.schwab.urlshortener.dto.response.ApiResponse;
import com.schwab.urlshortener.dto.response.ShortenUrlResponse;
import com.schwab.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {
  private final UrlShortenerService urlShortenerService;

  @PostMapping
  public ResponseEntity<ApiResponse<ShortenUrlResponse>> shorten(
      @Valid @RequestBody ShortenUrlRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    ShortenUrlResponse response = urlShortenerService.shorten(request, userDetails.getUsername());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(response, "URL shortened successfully"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ShortenUrlResponse>>> getUserUrls(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @AuthenticationPrincipal UserDetails userDetails) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<ShortenUrlResponse> responses =
        urlShortenerService.getUserUrls(userDetails.getUsername(), pageable);
    return ResponseEntity.ok(ApiResponse.ok(responses));
  }

  @GetMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<ShortenUrlResponse>> getByShortCode(
      @PathVariable String shortCode, @AuthenticationPrincipal UserDetails userDetails) {
    ShortenUrlResponse response =
        urlShortenerService.getByShortCode(shortCode, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<ShortenUrlResponse>> update(
      @PathVariable String shortCode,
      @Valid @RequestBody UpdateUrlRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    ShortenUrlResponse response =
        urlShortenerService.update(shortCode, request, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(response, "URL updated successfully"));
  }

  @DeleteMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable String shortCode, @AuthenticationPrincipal UserDetails userDetails) {
    urlShortenerService.delete(shortCode, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(null, "URL deleted successfully"));
  }
}
