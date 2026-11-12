package com.github.yehortpk.telegrambot;

import com.github.yehortpk.telegrambot.exceptions.TelegramRuntimeException;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;

/**
 * {@link Executor} is the class that takes a {@link DefaultAbsSender} bot and execute some {@link BotApiMethod}
 * Telegram Api method
 *
 */
@RequiredArgsConstructor
public class Executor {
    private final DefaultAbsSender bot;

    /**
     *
     * @param method {@link BotApiMethod} (Telegram API command)
     * @return the result of executing the {@link BotApiMethod} (Telegram API command)
     */
    public <T extends Serializable> Serializable  execute(BotApiMethod<T> method) {
        try{
            return bot.execute(method);
        } catch (TelegramApiException e) {
            throw new TelegramRuntimeException(e.getMessage());
        }
    }
}
