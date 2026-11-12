package com.github.yehortpk.telegrambot.handlers;

import com.github.yehortpk.telegrambot.models.UserRequestDTO;
import com.github.yehortpk.telegrambot.services.TelegramMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public abstract class RequestHandlerImpl implements RequestHandler {
    @Autowired
    protected TelegramMessageSender telegramMessageSender;

    private final int TG_MESSAGE_MAX_LENGTH = 4000;

    @Override
    public void handle(UserRequestDTO userRequest) {
        SendMessage sendMessage = handleRequest(userRequest);
        long chatId = Long.parseLong(sendMessage.getChatId());
        String text = sendMessage.getText();
        ReplyKeyboard markup = sendMessage.getReplyMarkup();

        if(text.length() > TG_MESSAGE_MAX_LENGTH) {
            List<String> dividedLargeText = getDividedLargeText(text);
            for (int i = 0; i < dividedLargeText.size() - 1; i++) {
                String textPart = dividedLargeText.get(i);
                telegramMessageSender.sendMessageWithoutMarkup(chatId, textPart);
            }
            String lastTextPart = dividedLargeText.getLast();
            telegramMessageSender.sendMessageWithMarkup(chatId, lastTextPart, markup);
        } else {
            telegramMessageSender.sendMessageWithMarkup(chatId, text, markup);
        }

    }

    // We need to find last close </a> tag to not crash tag parsing
    // All the residuals from string after the tag are going to the next message
    // todo check by TG_MESSAGE_MAX_LENGTH, it could be bigger
    private List<String> getDividedLargeText(String text) {
        List<String> result = new ArrayList<>();
        int residuals = 0;
        for (int i = 0; i < (text.length() / TG_MESSAGE_MAX_LENGTH); i++) {
            int lowerBoundIndex = i * TG_MESSAGE_MAX_LENGTH - residuals;

            int upperBoundIndex = (i + 1) * TG_MESSAGE_MAX_LENGTH;

            String rawSubstring = text.substring(i * TG_MESSAGE_MAX_LENGTH, upperBoundIndex);
            int lastClosedTagIndex = rawSubstring.lastIndexOf("</a>");
            int closedTagIndex = i * TG_MESSAGE_MAX_LENGTH + (
                    lastClosedTagIndex == -1?
                            TG_MESSAGE_MAX_LENGTH:
                            lastClosedTagIndex + 4
            );

            String taggedSubstring = text.substring(
                    lowerBoundIndex,
                    closedTagIndex
            );

            residuals = (i + 1) * TG_MESSAGE_MAX_LENGTH - closedTagIndex;
            result.add(taggedSubstring);
        }

        int lastPartLowerBoundIndex = text.length() - (text.length() % TG_MESSAGE_MAX_LENGTH + residuals);
        String lastSubstring = text.substring(
                lastPartLowerBoundIndex
        );
        result.add(lastSubstring);

        return result;
    }

    public abstract SendMessage handleRequest(UserRequestDTO userRequest);
}
