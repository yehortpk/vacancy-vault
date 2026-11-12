package com.github.yehortpk.subscriberbot.models;

/**
 * The router's answer to {@code /status}: whether this chat is linked, to which account, and
 * how many of that account's filters deliver here ({@code activeFilterCount}) out of all of
 * them ({@code filterCount}). {@code email} is null when the chat is not linked.
 */
public record ConnectorStatusDTO(
        boolean linked,
        String email,
        long filterCount,
        long activeFilterCount) {
}
