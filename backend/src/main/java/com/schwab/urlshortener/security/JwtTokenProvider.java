package com.schwab.urlshortener.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class JwtTokenProvider {
  private final SecretKey secretKey;
  private final long expirationMs;

  public JwtTokenProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-ms}") long expirationMs) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }

  public String generateToken(Authentication authentication) {
    String username;
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserDetails userDetails) {
      username = userDetails.getUsername();
    } else {
      username = principal.toString();
    }
    return generateToken(username);
  }

  public String generateToken(String username) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);
    return Jwts.builder()
        .subject(username)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey, Jwts.SIG.HS256)
        .compact();
  }

  public String getUsernameFromToken(String token) {
    return parseClaims(token).getSubject();
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT token is expired: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.warn("JWT token is unsupported: {}", e.getMessage());
    } catch (MalformedJwtException e) {
      log.warn("JWT token is malformed: {}", e.getMessage());
    } catch (SignatureException e) {
      log.warn("JWT signature validation failed: {}", e.getMessage());
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT token validation failed: {}", e.getMessage());
    }
    return false;
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public long getExpirationMs() {
    return expirationMs;
  }
}
