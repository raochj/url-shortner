package com.schwab.urlshortener.security;

import com.schwab.urlshortener.entity.User;
import com.schwab.urlshortener.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + username));
    if (!user.isActive()) {
      throw new UsernameNotFoundException("User account is disabled: " + username);
    }
    return buildUserDetails(user);
  }

  private UserDetails buildUserDetails(User user) {
    Collection<GrantedAuthority> authorities = buildAuthorities(user.getRole());
    return new org.springframework.security.core.userdetails.User(
        user.getUsername(), user.getPasswordHash(), user.isActive(), true, true, true, authorities);
  }

  private Collection<GrantedAuthority> buildAuthorities(String role) {
    String normalizedRole =
        (role != null && !role.isBlank()) ? role.toUpperCase(Locale.ROOT) : "USER";
    String authority =
        normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
    return List.of(new SimpleGrantedAuthority(authority));
  }
}
