package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.request.LoginRequest;
import com.schwab.urlshortener.dto.request.RegisterRequest;
import com.schwab.urlshortener.dto.response.AuthResponse;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.exception.DuplicateAliasException;
import com.schwab.urlshortener.repository.UserRepository;
import com.schwab.urlshortener.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuthenticationManager authenticationManager;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
    String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
    if (userRepository.existsByUsername(username)) {
      throw new DuplicateAliasException(username, "username");
    }
    if (userRepository.existsByEmail(email)) {
      throw new DuplicateAliasException(email, "email");
    }
    User user =
        User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .role("USER")
            .isActive(true)
            .build();
    userRepository.save(user);
    log.info("Registered new user: username={}, role={}", user.getUsername(), user.getRole());
    String token = jwtTokenProvider.generateToken(user.getUsername());
    return AuthResponse.of(
        token,
        jwtTokenProvider.getExpirationMs(),
        user.getUsername(),
        user.getEmail(),
        user.getRole());
  }

  public AuthResponse login(LoginRequest request) {
    String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, request.password()));
    String token = jwtTokenProvider.generateToken(authentication);
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    log.info("User logged in: username={}", user.getUsername());
    return AuthResponse.of(
        token,
        jwtTokenProvider.getExpirationMs(),
        user.getUsername(),
        user.getEmail(),
        user.getRole());
  }

  @Transactional(readOnly = true)
  public User getCurrentUser(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }
}
