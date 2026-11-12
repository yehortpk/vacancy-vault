package com.github.yehortpk.router.providers.telegram;

/** Where Telegram delivers: a chat id, negative for groups and never zero. */
public record TelegramDestination(long chatId) {
}
