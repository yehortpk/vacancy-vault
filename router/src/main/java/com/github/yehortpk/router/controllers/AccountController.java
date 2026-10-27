package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.user.AccountDTO;
import com.github.yehortpk.router.security.AuthenticatedUser;
import com.github.yehortpk.router.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for handling requests about the caller's own account.
 *
 * <p>The user is taken from the authenticated principal, never from the request, so there is
 * no id for a caller to substitute.
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @GetMapping
    public AccountDTO getAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.findAccount(principal.getUserId());
    }
}
