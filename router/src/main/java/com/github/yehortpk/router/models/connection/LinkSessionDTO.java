package com.github.yehortpk.router.models.connection;

import java.time.LocalDateTime;

/**
 * A link session as the account panel sees it.
 *
 * <p>{@code token} and {@code instructions} are only present in the response that started
 * the session: the token is not stored, and the instructions are the provider's
 * {@code link_instructions} with the token filled in. {@code connection} is present once the
 * session is linked.
 */
public record LinkSessionDTO(
        long id,
        long providerId,
        LinkSessionStatus status,
        LocalDateTime expiresAt,
        String token,
        String instructions,
        ConnectionDTO connection) {
}
