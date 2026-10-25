package com.schwab.urlshortener.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
    boolean success, T data, String message, String errorCode, Instant timestamp) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, Instant.now());
  }

  public static <T> ApiResponse<T> ok(T data, String message) {
    return new ApiResponse<>(true, data, message, null, Instant.now());
  }

  public static <T> ApiResponse<T> error(String message, String errorCode) {
    return new ApiResponse<>(false, null, message, errorCode, Instant.now());
  }
}
