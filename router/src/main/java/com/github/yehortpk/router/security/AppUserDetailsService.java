package com.github.yehortpk.router.security;

import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    private AuthenticatedUser toPrincipal(AppUser user) {
        return new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.isEnabled(), user.getRole());
    }

    @Override
    public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(this::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }
}
