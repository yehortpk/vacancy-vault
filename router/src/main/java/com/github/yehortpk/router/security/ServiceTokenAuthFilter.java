package com.github.yehortpk.router.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates the bot as a service from a shared secret header.
 *
 * <p>The connector endpoints are called by the bot, not a browser, and some of them must
 * work for a chat that has no API key to present — {@code /status} and {@code /unlink} for
 * an unlinked chat. So service-level auth is needed regardless; the user's own API key
 * travels in the link body on top of it, which keeps per-user authorization without giving
 * the bot blanket authority to bind any chat to any account.
 */
@Slf4j
public class ServiceTokenAuthFilter extends OncePerRequestFilter {
    static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    static final String SERVICE_ROLE = "ROLE_SERVICE";

    private final byte[] expectedToken;

    public ServiceTokenAuthFilter(@Value("${service.connector-token}") String serviceToken) {
        this.expectedToken = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(SERVICE_TOKEN_HEADER);

        if (presented != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Constant-time: a byte-by-byte comparison that returns early leaks the token
            // one character at a time to a caller who can measure the difference.
            if (MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedToken)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("service", null,
                                List.of(new SimpleGrantedAuthority(SERVICE_ROLE))));
            } else {
                log.warn("Rejected a service token on {}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
