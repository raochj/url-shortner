package com.schwab.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 encoder/decoder using the alphanumeric alphabet. Suitable for producing compact, URL-safe
 * short codes from numeric IDs.
 */
@Component
public class Base62Encoder {
  public static final String ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final int BASE = ALPHABET.length(); // 62

  /**
   * Encodes a positive long value into a Base62 string. Returns at least one character; encodes 0
   * as "0".
   */
  public String encode(long id) {
    if (id < 0) {
      throw new IllegalArgumentException("ID must be non-negative, got: " + id);
    }
    if (id == 0) {
      return String.valueOf(ALPHABET.charAt(0));
    }
    StringBuilder sb = new StringBuilder();
    long remaining = id;
    while (remaining > 0) {
      sb.append(ALPHABET.charAt((int) (remaining % BASE)));
      remaining /= BASE;
    }
    return sb.reverse().toString();
  }

  /**
   * Decodes a Base62 string back to a long value.
   *
   * @throws IllegalArgumentException if the string contains characters outside the alphabet
   */
  public long decode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Code must not be null or blank");
    }
    long result = 0L;
    for (char c : code.toCharArray()) {
      int index = ALPHABET.indexOf(c);
      if (index < 0) {
        throw new IllegalArgumentException("Invalid character '" + c + "' in Base62 code: " + code);
      }
      result = result * BASE + index;
    }
    return result;
  }

  /** Returns true if every character in the given string belongs to the Base62 alphabet. */
  public boolean isValid(String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    for (char c : code.toCharArray()) {
      if (ALPHABET.indexOf(c) < 0) {
        return false;
      }
    }
    return true;
  }
}
