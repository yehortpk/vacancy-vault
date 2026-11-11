package com.github.yehortpk.router.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates a request from its {@code Authorization: Bearer} header.
 *
 * <p>A bad token is not rejected here, only left unauthenticated: the chain's access rules
 * decide what that costs, which keeps the public endpoints reachable when a stale token is
 * still sitting in a browser's local storage.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        AuthenticatedUser principal = new AuthenticatedUser(
                jwtService.extractUserId(claims), jwtService.extractEmail(claims), null, true,
                jwtService.extractRole(claims));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseToken(token);
                // A refresh token is a credential for /auth/refresh only; honouring it as a
                // bearer token would silently give it the access token's 15-minute scope
                // for 30 days.
                if (jwtService.isAccessToken(claims)) {
                    authenticate(request, claims);
                } else {
                    log.debug("Rejected a non-access token on {}", request.getRequestURI());
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected a bearer token on {}: {}", request.getRequestURI(), ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
