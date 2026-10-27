package com.github.yehortpk.router.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and parses the access and refresh tokens.
 *
 * <p>The subject is the user id, so a token survives an email change, and the email rides
 * along as a claim for logging and for the {@code /me} response. The token type is a claim
 * too: a refresh token must not be accepted as a bearer credential, and the only thing
 * distinguishing the two is what is inside them.
 */
@Service
public class JwtService {
    static final String TOKEN_TYPE_CLAIM = "type";
    static final String ACCESS_TOKEN_TYPE = "access";
    static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey signingKey;
    private final Duration accessTokenTTL;
    private final Duration refreshTokenTTL;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-token-ttl}") Duration accessTokenTTL,
                      @Value("${jwt.refresh-token-ttl}") Duration refreshTokenTTL) {
        this.signingKey = buildSigningKey(secret);
        this.accessTokenTTL = accessTokenTTL;
        this.refreshTokenTTL = refreshTokenTTL;
    }

    /**
     * HS256 needs at least 256 bits of key. A Base64 secret is decoded so a 32-byte random
     * value can be configured as 44 characters; anything else is taken as raw text, which
     * keeps a hand-written development secret usable.
     */
    private static SecretKey buildSigningKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must decode to at least 32 bytes for HS256, got " + keyBytes.length);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateToken(long userId, String email, String tokenType, Duration ttl) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(EMAIL_CLAIM, email)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public String generateAccessToken(long userId, String email) {
        return generateToken(userId, email, ACCESS_TOKEN_TYPE, accessTokenTTL);
    }

    public String generateRefreshToken(long userId, String email) {
        return generateToken(userId, email, REFRESH_TOKEN_TYPE, refreshTokenTTL);
    }

    /**
     * Verifies the signature and expiry and returns the claims.
     *
     * @throws JwtException if the token is malformed, expired or tampered with
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return claims.get(EMAIL_CLAIM, String.class);
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }
}
