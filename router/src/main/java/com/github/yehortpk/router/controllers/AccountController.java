package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.connection.ConnectionDTO;
import com.github.yehortpk.router.models.connection.LinkSessionDTO;
import com.github.yehortpk.router.models.connection.RenameConnectionRequestDTO;
import com.github.yehortpk.router.models.provider.ProviderDTO;
import com.github.yehortpk.router.models.user.AccountDTO;
import com.github.yehortpk.router.repositories.ProviderRepository;
import com.github.yehortpk.router.security.AuthenticatedUser;
import com.github.yehortpk.router.services.AccountService;
import com.github.yehortpk.router.services.ConnectionLinkService;
import com.github.yehortpk.router.services.ConnectionService;
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
    private final ConnectionService connectionService;
    private final ConnectionLinkService connectionLinkService;
    private final ProviderRepository providerRepository;

    @GetMapping
    public AccountDTO getAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.findAccount(principal.getUserId());
    }

    /** The providers the user can link, in the catalog's order. */
    @GetMapping("/providers")
    public List<ProviderDTO> getProviders() {
        return providerRepository.findEnabled().stream()
                .map(provider -> new ProviderDTO(provider.getId(), provider.getTitle()))
                .toList();
    }

    @GetMapping("/connections")
    public List<ConnectionDTO> getConnections(@AuthenticationPrincipal AuthenticatedUser principal) {
        return connectionService.findConnections(principal.getUserId());
    }

    /**
     * Starts linking a destination. The response carries the one-time token and the
     * provider's instructions with it filled in; neither is shown again.
     */
    @PostMapping("/connections/{provider_id}/link")
    public LinkSessionDTO startLink(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable("provider_id") long providerId) {
        return connectionLinkService.startLink(principal.getUserId(), providerId);
    }

    @GetMapping("/link-sessions/{session_id}")
    public LinkSessionDTO getLinkSession(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable("session_id") long sessionId) {
        return connectionLinkService.findLinkSession(sessionId, principal.getUserId());
    }

    @PatchMapping("/connections/{connection_id}")
    public ConnectionDTO renameConnection(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable("connection_id") long connectionId,
                                          @Valid @RequestBody RenameConnectionRequestDTO request) {
        return connectionService.renameConnection(connectionId, principal.getUserId(), request.title());
    }

    @DeleteMapping("/connections/{connection_id}")
    public void deleteConnection(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable("connection_id") long connectionId) {
        connectionService.deleteOwnedConnection(connectionId, principal.getUserId());
    }
}
