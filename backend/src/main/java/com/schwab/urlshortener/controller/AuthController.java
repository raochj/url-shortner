package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.request.LoginRequest;
import com.schwab.urlshortener.dto.request.RegisterRequest;
import com.schwab.urlshortener.dto.response.ApiResponse;
import com.schwab.urlshortener.dto.response.AuthResponse;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(response, "User registered successfully"));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Map<String, Object>>> me(
      @AuthenticationPrincipal UserDetails userDetails) {
    User user = authService.getCurrentUser(userDetails.getUsername());
    Map<String, Object> info =
        Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "role", user.getRole(),
            "isActive", user.isActive());
    return ResponseEntity.ok(ApiResponse.ok(info));
  }
}
