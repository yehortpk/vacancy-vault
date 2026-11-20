package com.github.yehortpk.parser.security;

import com.github.yehortpk.parser.models.admin.Admin;
import com.github.yehortpk.parser.repositories.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the panel's operators from the {@code admin} table for the login check.
 */
@Service
@RequiredArgsConstructor
public class AdminDetailsService implements UserDetailsService {
    static final String ADMIN_ROLE = "ADMIN";

    private final AdminRepository adminRepository;

    private UserDetails createUserDetails(Admin admin) {
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .roles(ADMIN_ROLE)
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminRepository.findByUsername(username)
                .map(this::createUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("No admin named " + username));
    }
}
