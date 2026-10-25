package com.schwab.urlshortener.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Produces a stable, non-reversible identifier for privacy-conscious unique-visitor analysis. */
@Component
public final class IpAddressAnonymizer {
  private final SecretKeySpec key;

  public IpAddressAnonymizer(@Value("${app.analytics.ip-hash-secret}") String secret) {
    if (secret.length() < 32) {
      throw new IllegalArgumentException(
          "Analytics IP hash secret must contain at least 32 characters");
    }
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  public String anonymize(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) return null;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(ipAddress.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to anonymize client address", ex);
    }
  }
}
