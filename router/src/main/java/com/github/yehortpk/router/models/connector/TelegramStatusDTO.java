package com.github.yehortpk.router.models.connector;

/**
 * What {@code /status} tells a chat about itself.
 *
 * <p>{@code email} is null when the chat is not linked. {@code activeFilterCount} counts
 * only filters bound to this chat; the difference from {@code filterCount} is the user's
 * drafts, which match nothing until they are bound.
 */
public record TelegramStatusDTO(
        boolean linked,
        String email,
        long filterCount,
        long activeFilterCount) {

    public static TelegramStatusDTO notLinked() {
        return new TelegramStatusDTO(false, null, 0, 0);
    }
}
