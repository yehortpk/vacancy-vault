package com.github.yehortpk.router.models.user;

import java.time.OffsetDateTime;

/**
 * The account as its owner sees it.
 *
 * <p>The role is included so the frontend can decide which rooms to offer; the router
 * enforces it on every request regardless.
 */
public record AccountDTO(
        long id,
        String email,
        UserRole role,
        OffsetDateTime createdAt) {
}
