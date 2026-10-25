package com.schwab.urlshortener.security;

public final class SecurityConstants {
  private SecurityConstants() {
    // Utility class — not instantiable
  }

  public static final String TOKEN_PREFIX = "Bearer ";
  public static final String HEADER_STRING = "Authorization";
  public static final String ROLE_USER = "ROLE_USER";
  public static final String ROLE_ADMIN = "ROLE_ADMIN";
}
