package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.service.RedirectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles short-code redirect requests at the root path.
 *
 * <p>The path variable regex ({shortCode:[a-zA-Z0-9]{4,10}}) restricts this controller to
 * alphanumeric codes of 4–10 characters, ensuring it never conflicts with /api/, /swagger-ui/,
 * /api-docs/, or /actuator/ paths.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {
  private final RedirectService redirectService;

  /**
   * Resolves a short code to its target long URL and issues a 302 Found redirect.
   *
   * <p>The regex on the path variable acts as the exclusion filter: only 4-10 character
   * alphanumeric tokens will route here. All other paths (api, swagger-ui, api-docs, actuator) do
   * not match [a-zA-Z0-9]{4,10} and are handled by their own controllers.
   */
  @GetMapping("/{shortCode:[a-zA-Z0-9]{4,10}}")
  public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
    log.debug("Redirect request for shortCode={}", shortCode);
    return redirectService.redirect(shortCode, request);
  }
}
