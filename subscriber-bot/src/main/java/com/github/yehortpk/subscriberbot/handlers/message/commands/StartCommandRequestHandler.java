package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Explains what the bot is for. Filters are managed in the web app; the bot only delivers
 * their notifications to the chats linked there.
 */
@Component
public class StartCommandRequestHandler extends CommandRequestHandlerImpl {
    static final String START_TEXT = """
            I deliver VacancyVault notifications about new vacancies that match your filters.

            To receive them in this chat:
            1. Open your account page in the VacancyVault web app and click <b>Link Telegram</b>.
            2. Send me the command it shows: <code>/link your-code</code>.

            Then create filters in the web app and choose this chat for them.

            /status shows which account this chat is linked to
            /unlink stops notifications to this chat""";

    @Override
    public String getCommand() {
        return "/start";
    }

    @Override
    public SendMessage handleRequest(UserRequestDTO userRequest) {
        return SendMessage.builder()
                .chatId(userRequest.getChatId())
                .text(START_TEXT)
                .build();
    }
}
