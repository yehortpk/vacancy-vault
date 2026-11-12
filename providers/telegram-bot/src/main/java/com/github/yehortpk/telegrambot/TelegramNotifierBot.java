package com.github.yehortpk.telegrambot;

import com.github.yehortpk.telegrambot.exceptions.MissedHandlerException;
import com.github.yehortpk.telegrambot.handlers.Dispatcher;
import com.github.yehortpk.telegrambot.handlers.RequestHandler;
import com.github.yehortpk.telegrambot.handlers.message.MessageRequestHandler;
import com.github.yehortpk.telegrambot.handlers.undefined.UndefinedRequestTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramBot;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram API bot for notifications about a new vacancy. Based on long polling queries (extends {@link TelegramLongPollingBot}).
 * Main purpose of the class to receive and handle users' {@link Update} updates, using {@link RequestHandler} handlers.
 *
 * <p>The bot is a link and notification endpoint only: filters are managed in the web app, and
 * the bot answers just the commands that link a chat to an account and report on it.
 */
@Component
@Slf4j
public class TelegramNotifierBot extends TelegramLongPollingBot implements TelegramBot {
    private final ApplicationContext applicationContext;

    @Value("${TG_BOT_USERNAME}")
    private String botUsername;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Autowired
    public TelegramNotifierBot(@Value("${TG_BOT_API_KEY}") String apiKey, ApplicationContext applicationContext) {
        super(apiKey);
        this.applicationContext = applicationContext;
    }

    /**
     * Handles incoming {@link Update} update, select appropriate handlers by message type, and execute them through
     * {@link Dispatcher}.
     *
     * @param update Update received
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            throw new MissedHandlerException("Handler is missed");
        }

        // All the beans that handle plain messages or commands are checked to see if the
        // Dispatcher can apply the update to them
        List<RequestHandler> handlersList = new ArrayList<>(
                applicationContext.getBeansOfType(MessageRequestHandler.class).values());
        // If no one is applicable - run undefined request handler
        handlersList.add(applicationContext.getBean(UndefinedRequestTypeHandler.class));
        Dispatcher dispatcher = new Dispatcher(handlersList, update.getMessage().getChatId());
        dispatcher.dispatch(update);
    }

    @Override
    public void clearWebhook() {}
}
