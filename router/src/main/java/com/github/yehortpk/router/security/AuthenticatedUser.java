package com.github.yehortpk.router.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Carries the user id as well as the email: every per-user endpoint scopes its queries
 * by id, and taking that id from the principal rather than the request body is what closes
 * the IDOR the old {@code clientId}-in-the-body endpoints had.
 */
@Getter
public class AuthenticatedUser implements UserDetails {
    private final long userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;

    public AuthenticatedUser(long userId, String email, String passwordHash, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
