package com.schwab.urlshortener.util;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Generates random 6-character Base62 short codes using a cryptographically secure random number
 * generator.
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {
  private static final int CODE_LENGTH = 8;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final Base62Encoder base62Encoder;

  /**
   * Produces an 8-character random string drawn from the Base62 alphabet. Entropy: 62^8 = ~218
   * trillion possible codes.
   */
  public String generate() {
    String alphabet = Base62Encoder.ALPHABET;
    int alphabetLength = alphabet.length();
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabetLength)));
    }
    return sb.toString();
  }

  /**
   * Generates a short code derived from a numeric ID (e.g. database sequence value). Pads to at
   * least CODE_LENGTH characters with a random prefix if needed.
   */
  public String generateFromId(long id) {
    String encoded = base62Encoder.encode(id);
    if (encoded.length() >= CODE_LENGTH) {
      return encoded.substring(0, CODE_LENGTH);
    }
    // Pad with random chars to reach minimum length
    StringBuilder sb = new StringBuilder();
    int padNeeded = CODE_LENGTH - encoded.length();
    String alphabet = Base62Encoder.ALPHABET;
    for (int i = 0; i < padNeeded; i++) {
      sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
    }
    sb.append(encoded);
    return sb.toString();
  }
}
