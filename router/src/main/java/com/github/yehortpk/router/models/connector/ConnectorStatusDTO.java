package com.github.yehortpk.router.models.connector;

/**
 * What a provider service may tell a destination about itself, e.g. the bot's {@code /status}.
 *
 * <p>{@code email} is null when the destination is not linked. {@code activeFilterCount}
 * counts only filters bound to this destination; the difference from {@code filterCount} is
 * the user's drafts, which match nothing until they are bound.
 */
public record ConnectorStatusDTO(
        boolean linked,
        String email,
        long filterCount,
        long activeFilterCount) {

    public static ConnectorStatusDTO notLinked() {
        return new ConnectorStatusDTO(false, null, 0, 0);
    }
}
