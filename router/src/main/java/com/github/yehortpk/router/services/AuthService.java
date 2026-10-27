package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.EmailAlreadyRegisteredException;
import com.github.yehortpk.router.exception.InvalidTokenException;
import com.github.yehortpk.router.models.auth.LoginRequestDTO;
import com.github.yehortpk.router.models.auth.RegisterRequestDTO;
import com.github.yehortpk.router.models.auth.TokenPairDTO;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Registration, login and refresh.
 *
 * <p>Passwords are only ever stored as BCrypt hashes, and login delegates the comparison to
 * the {@link AuthenticationManager} so a wrong password and an unknown email fail the same
 * way, without leaking which of the two it was.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    private TokenPairDTO issueTokens(AppUser user) {
        return new TokenPairDTO(
                jwtService.generateAccessToken(user.getId(), user.getEmail()),
                jwtService.generateRefreshToken(user.getId(), user.getEmail()));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase();
    }

    @Transactional
    public TokenPairDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(true);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations both pass the existsByEmail check; the unique
            // constraint is what actually decides, so report it the same way.
            throw new EmailAlreadyRegisteredException(email);
        }

        return issueTokens(user);
    }

    public TokenPairDTO login(LoginRequestDTO request) {
        String email = normalizeEmail(request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        AppUser user = userRepository.findByEmail(email).orElseThrow();
        return issueTokens(user);
    }

    public TokenPairDTO refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Refresh token is not valid");
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw new InvalidTokenException("Expected a refresh token");
        }

        // The account may have been deleted or disabled since the token was issued, so the
        // row is reloaded rather than trusted from the claims.
        AppUser user = userRepository.findById(jwtService.extractUserId(claims))
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new InvalidTokenException("Account is no longer active"));

        return issueTokens(user);
    }
}
