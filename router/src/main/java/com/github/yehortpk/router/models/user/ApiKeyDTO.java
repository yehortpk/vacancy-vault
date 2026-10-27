package com.github.yehortpk.router.models.user;

/**
 * A freshly generated API key, in clear text.
 *
 * <p>Returned exactly once, at generation: only the hash is stored, so it cannot be shown
 * again. If it leaks, the remedy is to regenerate, which invalidates the old key.
 */
public record ApiKeyDTO(String apiKey) {
}
