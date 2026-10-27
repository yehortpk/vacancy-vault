package com.github.yehortpk.router.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for token issuing and verification. No Spring context: the service's only
 * collaborators are its three configured values.
 */
class JwtServiceTest {
    private static final String SECRET = "a-development-secret-long-enough-for-hs256-signing";
    private static final long USER_ID = 7L;
    private static final String EMAIL = "owner@example.com";

    private JwtService jwtService(Duration accessTokenTTL) {
        return new JwtService(SECRET, accessTokenTTL, Duration.ofDays(30));
    }

    private JwtService jwtService() {
        return jwtService(Duration.ofMinutes(15));
    }

    /** Flips the last character of the signature, leaving the payload intact. */
    private String withTamperedSignature(String token) {
        char last = token.charAt(token.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return token.substring(0, token.length() - 1) + replacement;
    }

    @Test
    void GenerateAccessToken_ValidUser_ShouldBeParseable() {
        JwtService jwtService = jwtService();

        Claims claims = jwtService.parseToken(jwtService.generateAccessToken(USER_ID, EMAIL));

        assertThat(jwtService.extractUserId(claims)).isEqualTo(USER_ID);
        assertThat(jwtService.extractEmail(claims)).isEqualTo(EMAIL);
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    @Test
    void GenerateRefreshToken_ValidUser_ShouldNotBeAcceptedAsAccessToken() {
        // The filter distinguishes the two only by this claim; a refresh token used as a
        // bearer credential would otherwise carry 30 days of access.
        JwtService jwtService = jwtService();

        Claims claims = jwtService.parseToken(jwtService.generateRefreshToken(USER_ID, EMAIL));

        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    void ParseToken_ExpiredToken_ShouldThrow() {
        // A negative TTL puts the expiry in the past, so no waiting is needed
        JwtService jwtService = jwtService(Duration.ofMinutes(-1));
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void ParseToken_TamperedSignature_ShouldThrow() {
        JwtService jwtService = jwtService();
        String tampered = withTamperedSignature(jwtService.generateAccessToken(USER_ID, EMAIL));

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void ParseToken_TokenSignedWithAnotherSecret_ShouldThrow() {
        String foreignToken = new JwtService(
                "a-totally-different-secret-also-long-enough-for-hs256", Duration.ofMinutes(15),
                Duration.ofDays(30))
                .generateAccessToken(USER_ID, EMAIL);
        JwtService jwtService = jwtService();

        assertThatThrownBy(() -> jwtService.parseToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void NewJwtService_SecretShorterThanHs256Requires_ShouldThrow() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(15), Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
