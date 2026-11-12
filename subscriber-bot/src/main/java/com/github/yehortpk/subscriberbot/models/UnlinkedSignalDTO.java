package com.github.yehortpk.subscriberbot.models;

import java.util.Map;

/**
 * The router's provider-independent "unlinked" signal, with the destination fields its
 * Telegram provider reads ({@code chatId}).
 */
public record UnlinkedSignalDTO(Map<String, String> destination) {
}
