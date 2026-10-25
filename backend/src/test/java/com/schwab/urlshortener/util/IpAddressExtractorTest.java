package com.schwab.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class IpAddressExtractorTest {

  private final IpAddressExtractor extractor = new IpAddressExtractor();

  @Test
  void ignoresForwardedHeaderFromPublicPeer() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("203.0.113.20");
    when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");

    assertThat(extractor.extract(request)).isEqualTo("203.0.113.20");
  }

  @Test
  void acceptsValidForwardedAddressFromInternalProxy() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("172.18.0.4");
    when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99, 172.18.0.2");

    assertThat(extractor.extract(request)).isEqualTo("198.51.100.99");
  }

  @Test
  void rejectsHostnameInForwardedHeader() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn("attacker.example");

    assertThat(extractor.extract(request)).isEqualTo("127.0.0.1");
  }
}
