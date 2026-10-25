package com.schwab.urlshortener.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Extracts the client IP address from an HTTP request.
 *
 * <p>Checks X-Forwarded-For but validates the first hop against known private/loopback ranges
 * before trusting it, preventing trivial IP spoofing by untrusted clients.
 */
@Slf4j
@Component
public class IpAddressExtractor {
  /**
   * Pattern matching private, loopback, link-local, and APIPA address ranges. These addresses
   * should NOT appear as the original client IP in a genuine public-internet request.
   */
  private static final Pattern PRIVATE_IP_PATTERN =
      Pattern.compile(
          "^(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
              + // 127.x.x.x loopback
              "|0\\.0\\.0\\.0"
              + // wildcard
              "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
              + // 10.x.x.x private
              "|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}"
              + // 172.16-31.x.x private
              "|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
              + // 192.168.x.x private
              "|169\\.254\\.\\d{1,3}\\.\\d{1,3}"
              + // 169.254.x.x link-local / APIPA
              "|::1"
              + // IPv6 loopback
              "|[fF][cCdD][0-9a-fA-F]{2}:.+)$" // IPv6 unique local
          );

  private static final String[] PROXY_HEADERS = {
    "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "True-Client-IP", "X-Client-IP"
  };
  private static final Pattern IP_LITERAL_PATTERN = Pattern.compile("^[0-9a-fA-F:.]+$");

  /**
   * Returns the best available client IP from the request.
   *
   * @param request the incoming HTTP request
   * @return a non-null IP string; falls back to {@code request.getRemoteAddr()}
   */
  public String extract(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    if (!isTrustedProxy(remoteAddress)) {
      return remoteAddress;
    }
    for (String header : PROXY_HEADERS) {
      String headerValue = request.getHeader(header);
      if (!StringUtils.hasText(headerValue)) {
        continue;
      }
      // X-Forwarded-For may contain a comma-separated chain; first value is the client
      String candidateIp = headerValue.split(",")[0].trim();
      if (isValidIp(candidateIp)) {
        return candidateIp;
      }
    }
    return remoteAddress;
  }

  /** Returns true if the given IP address is a routable (non-private, non-loopback) address. */
  public boolean isPublicIp(String ip) {
    if (!StringUtils.hasText(ip)) {
      return false;
    }
    return !PRIVATE_IP_PATTERN.matcher(ip.trim()).matches();
  }

  private boolean isTrustedProxy(String ip) {
    if (!StringUtils.hasText(ip)) return false;
    try {
      InetAddress address = InetAddress.getByName(ip.trim());
      return address.isLoopbackAddress()
          || address.isSiteLocalAddress()
          || address.isLinkLocalAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private boolean isValidIp(String ip) {
    if (!StringUtils.hasText(ip) || ip.length() > 45) return false;
    if (!IP_LITERAL_PATTERN.matcher(ip.trim()).matches()) return false;
    try {
      InetAddress.getByName(ip.trim());
      return true;
    } catch (UnknownHostException e) {
      log.debug("Ignoring malformed proxy IP value: {}", ip);
      return false;
    }
  }
}
