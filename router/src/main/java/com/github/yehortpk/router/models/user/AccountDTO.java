package com.github.yehortpk.router.models.user;

import java.time.LocalDateTime;

/**
 * The account as its owner sees it. The API key itself is never included — only whether one
 * exists and its lookup prefix, since the secret half is shown once at generation time.
 */
public record AccountDTO(
        long id,
        String email,
        LocalDateTime createdAt,
        String apiKeyPrefix,
        boolean hasApiKey) {
}
