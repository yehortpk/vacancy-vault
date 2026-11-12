package com.github.yehortpk.router.models.connection;

import java.time.OffsetDateTime;

/**
 * A link session as the account panel sees it.
 *
 * <p>{@code token} and {@code instructions} are only present in the response that started
 * the session: the token is not stored, and the instructions are the provider's
 * {@code link_instructions} with the token filled in.
 */
public record LinkSessionDTO(
        long id,
        long providerId,
        LinkSessionStatus status,
        OffsetDateTime expiresAt,
        String token,
        String instructions) {
}
