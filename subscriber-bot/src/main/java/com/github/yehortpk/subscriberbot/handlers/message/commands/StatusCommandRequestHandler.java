package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.models.ConnectorStatusDTO;
import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import com.github.yehortpk.subscriberbot.services.ConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Tells a chat which account it is linked to and how many of that account's filters deliver
 * here; the rest are drafts or bound to other chats.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatusCommandRequestHandler extends CommandRequestHandlerImpl {
    private final ConnectorService connectorService;

    private static String describeStatus(ConnectorStatusDTO status) {
        if (!status.linked()) {
            return "This chat is not linked to an account. Link Telegram on your account page "
                    + "to receive notifications here.";
        }
        // Replies are sent as HTML, so the address must not be read as markup
        return "This chat is linked to <b>%s</b>.\n%d of the account's %d filters deliver here."
                .formatted(HtmlUtils.htmlEscape(status.email()), status.activeFilterCount(), status.filterCount());
    }

    @Override
    public String getCommand() {
        return "/status";
    }

    @Override
    public SendMessage handleRequest(UserRequestDTO userRequest) {
        long chatId = userRequest.getChatId();

        String text;
        try {
            text = describeStatus(connectorService.findTelegramStatus(chatId));
        } catch (RestClientException ex) {
            log.error("Could not read the status of chat {}: {}", chatId, ex.getMessage());
            text = "The status of this chat could not be read right now. Please try again later.";
        }

        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }
}
