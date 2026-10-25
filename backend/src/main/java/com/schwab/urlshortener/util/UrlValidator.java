package com.schwab.urlshortener.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates URLs before shortening them.
 *
 * <p>Guards against SSRF (Server-Side Request Forgery) by rejecting URLs whose resolved host maps
 * to a private, loopback, link-local, or cloud-metadata address.
 */
@Slf4j
@Component
public class UrlValidator {
  /**
   * Regex that matches IPv4 addresses in private/reserved ranges: - 127.x.x.x (loopback) - 10.x.x.x
   * (RFC 1918 private) - 172.16-31.x.x (RFC 1918 private) - 192.168.x.x (RFC 1918 private) -
   * 169.254.x.x (link-local / APIPA, includes 169.254.169.254 cloud metadata) - 0.0.0.0 (wildcard)
   */
  private static final Pattern PRIVATE_IP_PATTERN =
      Pattern.compile(
          "^(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
              + "|0\\.0\\.0\\.0"
              + "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
              + "|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}"
              + "|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
              + "|169\\.254\\.\\d{1,3}\\.\\d{1,3})$");

  /**
   * Validates the supplied URL string.
   *
   * <p>Rules:
   *
   * <ol>
   *   <li>Must start with {@code http://} or {@code https://}
   *   <li>Must be parseable as a {@link java.net.URI}
   *   <li>Host must not be empty
   *   <li>Resolved IP must not fall within a private/loopback/link-local range (SSRF guard)
   * </ol>
   *
   * @param urlString the URL to validate
   * @return {@code true} if the URL is safe and well-formed; {@code false} otherwise
   */
  public boolean isValid(String urlString) {
    if (urlString == null || urlString.isBlank()) {
      return false;
    }
    String trimmed = urlString.trim();
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(trimmed);
    } catch (URISyntaxException e) {
      log.debug("Malformed URL rejected");
      return false;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return false;
    }
    // Block localhost by name
    if (host.equalsIgnoreCase("localhost")) {
      log.warn("SSRF guard: localhost rejected");
      return false;
    }
    // Resolve host and check resulting IP(s)
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress address : addresses) {
        String ipString = address.getHostAddress();
        if (address.isLoopbackAddress()
            || address.isSiteLocalAddress()
            || address.isLinkLocalAddress()
            || address.isAnyLocalAddress()
            || address.isMulticastAddress()
            || isPrivateIpRange(ipString)) {
          log.warn("SSRF guard rejected a host resolving to a private/reserved address");
          return false;
        }
      }
    } catch (UnknownHostException e) {
      // Cannot resolve host — treat as invalid to prevent blind SSRF
      log.debug("Unknown URL host rejected");
      return false;
    }
    return true;
  }

  /**
   * Secondary regex check covering ranges not always caught by {@link InetAddress} flags, such as
   * 169.254.169.254 (AWS instance metadata) or 0.0.0.0.
   */
  private boolean isPrivateIpRange(String ip) {
    return PRIVATE_IP_PATTERN.matcher(ip).matches();
  }
}
