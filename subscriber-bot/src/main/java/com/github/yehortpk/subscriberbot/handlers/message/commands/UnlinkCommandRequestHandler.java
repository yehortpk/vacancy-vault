package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import com.github.yehortpk.subscriberbot.services.ConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Stops notifications to this chat by unlinking it from its account. The account's filters
 * stay; those bound only to this chat become drafts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnlinkCommandRequestHandler extends CommandRequestHandlerImpl {
    private final ConnectorService connectorService;

    @Override
    public String getCommand() {
        return "/unlink";
    }

    @Override
    public SendMessage handleRequest(UserRequestDTO userRequest) {
        long chatId = userRequest.getChatId();

        String text;
        try {
            // Unlinking a chat that is not linked is already done, so both answer the same
            connectorService.unlinkTelegramConnection(chatId);
            text = "This chat is not linked to any account and will receive no notifications.";
        } catch (RestClientException ex) {
            log.error("Could not unlink chat {}: {}", chatId, ex.getMessage());
            text = "This chat could not be unlinked right now. Please try again later.";
        }

        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }
}
