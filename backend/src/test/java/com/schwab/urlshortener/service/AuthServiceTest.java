package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.dto.request.RegisterRequest;
import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.repository.UserRepository;
import com.schwab.urlshortener.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private AuthenticationManager authenticationManager;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(userRepository, passwordEncoder, jwtTokenProvider, authenticationManager);
  }

  @Test
  void publicRegistrationCannotAssignAdminRole() {
    when(passwordEncoder.encode("StrongPassword1!")).thenReturn("hash");
    when(jwtTokenProvider.generateToken("alice")).thenReturn("token");
    when(jwtTokenProvider.getExpirationMs()).thenReturn(86_400_000L);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    authService.register(
        new RegisterRequest("alice", "alice@example.com", "StrongPassword1!", "ADMIN"));

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getRole()).isEqualTo("USER");
  }
}
