package com.github.yehortpk.router.models.user;

import java.time.OffsetDateTime;

/**
 * The account as its owner sees it.
 */
public record AccountDTO(
        long id,
        String email,
        OffsetDateTime createdAt) {
}
