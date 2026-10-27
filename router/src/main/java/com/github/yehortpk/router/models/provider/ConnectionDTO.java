package com.github.yehortpk.router.models.provider;

import java.time.LocalDateTime;

/**
 * A linked destination as the account panel shows it.
 *
 * <p>{@code address} is the provider's own display form of the destination — for Telegram
 * the chat id — read from that provider's connection table and rendered as text, so one DTO
 * covers every messenger without the panel knowing their field names.
 */
public record ConnectionDTO(
        long id,
        ChannelProvider provider,
        String title,
        String address,
        LocalDateTime linkedAt) {
}
