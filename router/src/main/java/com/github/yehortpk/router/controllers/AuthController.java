package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.auth.LoginRequestDTO;
import com.github.yehortpk.router.models.auth.RefreshRequestDTO;
import com.github.yehortpk.router.models.auth.RegisterRequestDTO;
import com.github.yehortpk.router.models.auth.TokenPairDTO;
import com.github.yehortpk.router.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for handling registration and token issuing requests
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenPairDTO register(@Valid @RequestBody RegisterRequestDTO request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenPairDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenPairDTO refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return authService.refresh(request.refreshToken());
    }
}
