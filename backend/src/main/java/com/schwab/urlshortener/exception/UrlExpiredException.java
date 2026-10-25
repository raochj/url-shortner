package com.schwab.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {
  public UrlExpiredException(String message) {
    super(message);
  }

  public UrlExpiredException(String shortCode, String expiryInfo) {
    super("URL has expired: " + shortCode + " (expired: " + expiryInfo + ")");
  }
}
