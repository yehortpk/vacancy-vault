package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.provider.ChannelProvider;
import com.github.yehortpk.router.models.provider.ConnectionDTO;
import com.github.yehortpk.router.models.provider.RenameConnectionRequestDTO;
import com.github.yehortpk.router.models.user.AccountDTO;
import com.github.yehortpk.router.models.user.ApiKeyDTO;
import com.github.yehortpk.router.security.AuthenticatedUser;
import com.github.yehortpk.router.services.AccountService;
import com.github.yehortpk.router.services.ApiKeyService;
import com.github.yehortpk.router.services.ConnectionLinkService;
import com.github.yehortpk.router.services.ConnectionResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final ApiKeyService apiKeyService;
    private final ConnectionResolver connectionResolver;
    private final ConnectionLinkService connectionLinkService;

    @GetMapping
    public AccountDTO getAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.findAccount(principal.getUserId());
    }

    /**
     * (Re)generates the account's API key and returns it in clear text.
     *
     * <p>POST rather than GET: it replaces the stored key, so the previous one stops working.
     */
    @PostMapping("/api-key")
    public ApiKeyDTO generateApiKey(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new ApiKeyDTO(apiKeyService.generateApiKey(principal.getUserId()));
    }

    @GetMapping("/connections")
    public List<ConnectionDTO> getConnections(@AuthenticationPrincipal AuthenticatedUser principal) {
        return connectionResolver.findConnections(principal.getUserId());
    }

    @PatchMapping("/connections/{provider}/{connection_id}")
    public ConnectionDTO renameConnection(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable("provider") ChannelProvider provider,
                                          @PathVariable("connection_id") long connectionId,
                                          @Valid @RequestBody RenameConnectionRequestDTO request) {
        return connectionLinkService.renameConnection(
                provider, connectionId, principal.getUserId(), request.title());
    }

    @DeleteMapping("/connections/{provider}/{connection_id}")
    public void deleteConnection(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable("provider") ChannelProvider provider,
                                 @PathVariable("connection_id") long connectionId) {
        connectionLinkService.deleteConnection(provider, connectionId, principal.getUserId());
    }
}
