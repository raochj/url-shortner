package com.schwab.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.config.SecurityConfig;
import com.schwab.urlshortener.dto.request.ShortenUrlRequest;
import com.schwab.urlshortener.dto.response.ShortenUrlResponse;
import com.schwab.urlshortener.exception.GlobalExceptionHandler;
import com.schwab.urlshortener.exception.PolicyViolationException;
import com.schwab.urlshortener.security.DistributedRateLimiter;
import com.schwab.urlshortener.security.JwtAuthenticationFilter;
import com.schwab.urlshortener.security.JwtTokenProvider;
import com.schwab.urlshortener.security.RateLimitFilter;
import com.schwab.urlshortener.security.UserDetailsServiceImpl;
import com.schwab.urlshortener.service.UrlShortenerService;
import com.schwab.urlshortener.util.IpAddressExtractor;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  RateLimitFilter.class,
  GlobalExceptionHandler.class
})
@DisplayName("UrlController web layer tests")
class UrlControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private UrlShortenerService urlShortenerService;
  // Required by SecurityConfig / JwtAuthenticationFilter
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserDetailsServiceImpl userDetailsService;
  @MockitoBean private DistributedRateLimiter distributedRateLimiter;
  @MockitoBean private IpAddressExtractor ipAddressExtractor;
  private static final String BASE_PATH = "/api/v1/urls";

  @BeforeEach
  void allowRateLimitedRequests() {
    when(ipAddressExtractor.extract(any())).thenReturn("127.0.0.1");
    when(distributedRateLimiter.acquire(any(), any(), anyInt(), anyLong()))
        .thenReturn(new DistributedRateLimiter.Decision(true, 10, 60, false));
  }

  // =========================================================================
  // POST / → 201 with shortUrl
  // =========================================================================
  @Test
  @WithMockUser(username = "testuser", roles = "USER")
  @DisplayName("POST /api/v1/urls returns 201 with shortUrl in body")
  void shorten_validUrl_returns201WithShortUrl() throws Exception {
    ShortenUrlRequest request =
        new ShortenUrlRequest("https://www.schwab.com/some/long/path", null, null, null);
    ShortenUrlResponse mockResponse =
        new ShortenUrlResponse(
            "abc123",
            "http://localhost:8080/abc123",
            "https://www.schwab.com/some/long/path",
            null,
            true,
            null,
            0L,
            List.of(),
            OffsetDateTime.now());
    when(urlShortenerService.shorten(any(ShortenUrlRequest.class), eq("testuser")))
        .thenReturn(mockResponse);
    mockMvc
        .perform(
            post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.shortCode").value("abc123"))
        .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8080/abc123"));
  }

  // =========================================================================
  // POST / with invalid URL → 400
  // =========================================================================
  @Test
  @WithMockUser(username = "testuser", roles = "USER")
  @DisplayName("POST /api/v1/urls with invalid URL returns 400")
  void shorten_invalidUrl_returns400() throws Exception {
    ShortenUrlRequest request =
        new ShortenUrlRequest("http://192.168.1.1/internal", null, null, null);
    when(urlShortenerService.shorten(any(ShortenUrlRequest.class), eq("testuser")))
        .thenThrow(
            new PolicyViolationException(
                "SSRF", "The URL 'http://192.168.1.1/internal' is not allowed"));
    mockMvc
        .perform(
            post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @WithMockUser(username = "testuser", roles = "USER")
  @DisplayName("POST /api/v1/urls with blank longUrl returns 400 (bean validation)")
  void shorten_blankLongUrl_returns400() throws Exception {
    ShortenUrlRequest request = new ShortenUrlRequest("", null, null, null);
    mockMvc
        .perform(
            post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // =========================================================================
  // GET /{code} → 200 with url details
  // =========================================================================
  @Test
  @WithMockUser(username = "testuser", roles = "USER")
  @DisplayName("GET /api/v1/urls/{code} returns 200 with url details")
  void getByShortCode_existingCode_returns200() throws Exception {
    ShortenUrlResponse mockResponse =
        new ShortenUrlResponse(
            "abc123",
            "http://localhost:8080/abc123",
            "https://www.schwab.com/some/long/path",
            null,
            true,
            null,
            42L,
            List.of("finance", "schwab"),
            OffsetDateTime.now());
    when(urlShortenerService.getByShortCode(eq("abc123"), eq("testuser"))).thenReturn(mockResponse);
    mockMvc
        .perform(get(BASE_PATH + "/abc123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.shortCode").value("abc123"))
        .andExpect(jsonPath("$.data.longUrl").value("https://www.schwab.com/some/long/path"))
        .andExpect(jsonPath("$.data.clickCount").value(42));
  }
}
