package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.exceptions.TelegramRuntimeException;
import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import com.github.yehortpk.subscriberbot.services.ConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;

/**
 * Links this chat to an account with the one-time code from the account page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LinkCommandRequestHandler extends CommandRequestHandlerImpl {
    private final ConnectorService connectorService;

    /**
     * The code is single-use, but a failed link leaves it valid until it expires, and whoever
     * sends it links their chat to its owner. So it should not sit in a group's history.
     */
    private void deleteMessageWithLinkCode(long chatId, int messageId) {
        try {
            telegramServiceUtil.deleteMessage(chatId, messageId);
        } catch (TelegramRuntimeException ex) {
            log.warn("Could not delete the /link message in chat {}: {}", chatId, ex.getMessage());
        }
    }

    private String findChatTitle(UserRequestDTO userRequest) {
        Chat chat = userRequest.getUpdate().getMessage().getChat();
        return chat == null ? null : chat.getTitle();
    }

    private String linkChat(long chatId, String chatTitle, String token) {
        try {
            connectorService.linkTelegramConnection(chatId, chatTitle, token);
            return "This chat is now linked to your account.";
        } catch (HttpClientErrorException.Unauthorized ex) {
            return "This link code is not valid or has expired. Start linking again on your account page.";
        } catch (HttpClientErrorException.Conflict ex) {
            return "This chat is already linked to another account.";
        } catch (RestClientException ex) {
            log.error("Could not link chat {}: {}", chatId, ex.getMessage());
            return "This chat could not be linked right now. Please try again later.";
        }
    }

    @Override
    public String getCommand() {
        return "/link";
    }

    @Override
    public SendMessage handleRequest(UserRequestDTO userRequest) {
        long chatId = userRequest.getChatId();

        String token = findCommandArgument(userRequest);
        String text;
        if (token == null) {
            text = "Send the link code with the command: <code>/link your-code</code>. "
                    + "You get a code by linking Telegram on your account page.";
        } else {
            deleteMessageWithLinkCode(chatId, userRequest.getUpdate().getMessage().getMessageId());
            text = linkChat(chatId, findChatTitle(userRequest), token);
        }

        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }
}
