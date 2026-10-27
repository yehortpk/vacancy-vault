package com.github.yehortpk.router.models.provider;

import java.util.Map;

/**
 * A resolved delivery destination, ready to put on the wire.
 *
 * <p>The database stays typed per provider while the Kafka payload stays one schema: the
 * router resolves a concrete connection and flattens its address into a provider-tagged
 * map, and the bot-side implementation reads the keys it knows ({@code chatId} for
 * Telegram). Adding a messenger therefore changes no Kafka contract.
 */
public record ConnectionTarget(
        ChannelProvider provider,
        long connectionId,
        Map<String, String> address) {

    public static final String TELEGRAM_CHAT_ID = "chatId";

    public static ConnectionTarget ofTelegram(TelegramConnection connection) {
        return new ConnectionTarget(
                ChannelProvider.TELEGRAM,
                connection.getId(),
                Map.of(TELEGRAM_CHAT_ID, String.valueOf(connection.getChatId())));
    }
}
