package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.connection.ConnectionDTO;
import com.github.yehortpk.router.models.connector.ConnectorStatusDTO;
import com.github.yehortpk.router.models.connector.LinkedSignalDTO;
import com.github.yehortpk.router.models.connector.UnlinkedSignalDTO;
import com.github.yehortpk.router.services.ConnectionLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsible for the signals a provider's own service sends, such as the
 * Telegram bot's.
 *
 * <p>The same three endpoints serve every provider. The destination travels as plain fields
 * that the provider's code reads (Telegram: {@code chatId}), so the request shape never
 * changes per messenger. Authenticated by the service token; on {@code /linked} the user's
 * one-time token is the user's proof.
 */
@RestController
@RequestMapping("/connector/{provider_id}")
@RequiredArgsConstructor
public class ConnectorController {
    private final ConnectionLinkService connectionLinkService;

    @PostMapping("/linked")
    public ConnectionDTO completeLink(@PathVariable("provider_id") long providerId,
                                      @Valid @RequestBody LinkedSignalDTO request) {
        return connectionLinkService.completeLink(
                providerId, request.token(), request.title(), request.destination());
    }

    @PostMapping("/unlinked")
    public void unlinkDestination(@PathVariable("provider_id") long providerId,
                                  @Valid @RequestBody UnlinkedSignalDTO request) {
        connectionLinkService.unlinkDestination(providerId, request.destination());
    }

    /** The destination's fields are the query parameters, e.g. {@code ?chatId=473123822}. */
    @GetMapping("/status")
    public ConnectorStatusDTO getStatus(@PathVariable("provider_id") long providerId,
                                        @RequestParam Map<String, String> destination) {
        return connectionLinkService.findConnectorStatus(providerId, destination);
    }
}
