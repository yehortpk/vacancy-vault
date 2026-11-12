package com.github.yehortpk.telegrambot.handlers.undefined;

import com.github.yehortpk.telegrambot.models.UserRequestDTO;
import com.github.yehortpk.telegrambot.handlers.RequestHandler;
import com.github.yehortpk.telegrambot.handlers.RequestHandlerImpl;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Handler responsible for shown a "Can't handle this type of request". Uses if no one is applicable
 */
@Component
public class UndefinedRequestTypeHandler extends RequestHandlerImpl implements RequestHandler {
    @Override
    public boolean isApplicable(UserRequestDTO userRequest) {
        return true;
    }

    @Override
    public SendMessage handleRequest(UserRequestDTO userRequest) {
        String text = "I can't handle this type of message. Use the commands /start, /link, /status or /unlink";
        return SendMessage.builder()
                .chatId(userRequest.getChatId())
                .text(text)
                .build();
    }
}
