package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.connector.TelegramLinkRequestDTO;
import com.github.yehortpk.router.models.connector.TelegramStatusDTO;
import com.github.yehortpk.router.models.connector.TelegramUnlinkRequestDTO;
import com.github.yehortpk.router.models.provider.ConnectionDTO;
import com.github.yehortpk.router.services.ConnectionLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for handling link requests from the notification bots.
 *
 * <p>Namespaced per provider, matching the per-provider connection tables: the payloads are
 * typed to what each messenger actually supplies. Authenticated by the service token, with
 * the user's own API key carried in the link body as the user's proof.
 */
@RestController
@RequestMapping("/connector/telegram")
@RequiredArgsConstructor
public class ConnectorController {
    private final ConnectionLinkService connectionLinkService;

    @PostMapping("/link")
    public ConnectionDTO linkTelegramConnection(@Valid @RequestBody TelegramLinkRequestDTO request) {
        return connectionLinkService.linkTelegramConnection(request.chatId(), request.apiKey());
    }

    @DeleteMapping
    public void unlinkTelegramConnection(@RequestBody TelegramUnlinkRequestDTO request) {
        connectionLinkService.unlinkTelegramConnection(request.chatId());
    }

    @GetMapping("/status")
    public TelegramStatusDTO getTelegramStatus(@RequestParam("chatId") long chatId) {
        return connectionLinkService.findTelegramStatus(chatId);
    }
}
