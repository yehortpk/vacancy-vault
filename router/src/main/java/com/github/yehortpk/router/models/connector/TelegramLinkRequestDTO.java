package com.github.yehortpk.router.models.connector;

import jakarta.validation.constraints.NotBlank;

/**
 * The bot's {@code /link} payload.
 *
 * <p>Typed to what Telegram actually supplies: a numeric chat id, not a generic
 * {@code externalId} string. A future messenger gets its own namespace and its own body
 * shape rather than overloading this one.
 */
public record TelegramLinkRequestDTO(
        long chatId,
        @NotBlank String apiKey) {
}
