package com.schwab.urlshortener.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.dto.response.ApiResponse;
import com.schwab.urlshortener.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
  private final DistributedRateLimiter rateLimiter;
  private final IpAddressExtractor ipAddressExtractor;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<Policy> policy = policyFor(request);
    if (policy.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    Policy selected = policy.get();
    DistributedRateLimiter.Decision decision =
        rateLimiter.acquire(
            selected.name(),
            ipAddressExtractor.extract(request),
            selected.limit(),
            selected.windowSeconds());
    response.setHeader("X-RateLimit-Limit", Integer.toString(selected.limit()));
    response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
    if (decision.degraded()) response.setHeader("X-RateLimit-Degraded", "true");
    if (decision.allowed()) {
      filterChain.doFilter(request, response);
      return;
    }

    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiResponse.error("Request rate limit exceeded", "RATE_LIMIT_EXCEEDED"));
  }

  private Optional<Policy> policyFor(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) return Optional.empty();
    return switch (request.getRequestURI()) {
      case "/api/v1/auth/login" -> Optional.of(new Policy("login", 10, 60));
      case "/api/v1/auth/register" -> Optional.of(new Policy("registration", 5, 3600));
      case "/api/v1/urls" -> Optional.of(new Policy("url-create", 60, 60));
      case "/api/v1/orchestration/runs" -> Optional.of(new Policy("orchestration-start", 10, 60));
      default -> Optional.empty();
    };
  }

  private record Policy(String name, int limit, long windowSeconds) {}
}
