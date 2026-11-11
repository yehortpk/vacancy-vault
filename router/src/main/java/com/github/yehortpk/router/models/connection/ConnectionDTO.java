package com.github.yehortpk.router.models.connection;

import java.time.LocalDateTime;

/**
 * A linked destination as the account panel shows it.
 *
 * <p>{@code address} is the provider's own display form of the destination (for Telegram the
 * chat id), so one DTO covers every messenger without the panel knowing their fields. It is
 * null when the provider has no details row for the connection.
 */
public record ConnectionDTO(
        long id,
        long providerId,
        String title,
        String address,
        LocalDateTime linkedAt) {
}
