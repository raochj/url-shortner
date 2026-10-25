package com.schwab.urlshortener.dto.response;

public record AuthResponse(
    String accessToken,
    String tokenType,
    Long expiresIn,
    String username,
    String email,
    String role) {
  public static AuthResponse of(
      String accessToken, Long expiresIn, String username, String email, String role) {
    return new AuthResponse(accessToken, "Bearer", expiresIn, username, email, role);
  }
}
