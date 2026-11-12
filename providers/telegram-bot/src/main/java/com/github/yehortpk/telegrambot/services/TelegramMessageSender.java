package com.github.yehortpk.telegrambot.services;

import com.github.yehortpk.telegrambot.Executor;
import com.github.yehortpk.telegrambot.TelegramNotifierBot;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

/**
 * {@link TelegramMessageSender} class created for simplify connection between code and TelegramAPI endpoints.
 * Uses {@link BotApiMethod} methods, wrapped by widely used common methods.
 */
@Component
public class TelegramMessageSender {
    private final Executor executor;

    public TelegramMessageSender(TelegramNotifierBot telegramNotifierBot) {
        executor = new Executor(telegramNotifierBot);
    }

    /**
     * Send message to user-bot chat with specific markup
     *
     * @param chatId Bot-user chatId
     * @param text   Message to send
     * @param markup {@link ReplyKeyboardMarkup} markup to send
     * @return {@link Message} message that was sent
     */
    public Message sendMessageWithMarkup(long chatId, String text, ReplyKeyboard markup) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .build();

        if (markup != null) {
            sendMessage.setReplyMarkup(markup);
        }

        return (Message) executor.execute(sendMessage);
    }

    /**
     * Send message to user-bot chat without specific markup
     *
     * @param chatId Bot-user chatId
     * @param text   Message to send
     * @return {@link Message} message that was sent
     */
    public Message sendMessageWithoutMarkup(long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .build();

        return (Message) executor.execute(sendMessage);
    }

    /**
     * Delete specific message
     *
     * @param chatId    Bot-user chatId
     * @param messageId ID of message to deletion
     */
    public void deleteMessage(long chatId, int messageId) {
        DeleteMessage sendRemoveInfoMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();

        executor.execute(sendRemoveInfoMessage);
    }

}
