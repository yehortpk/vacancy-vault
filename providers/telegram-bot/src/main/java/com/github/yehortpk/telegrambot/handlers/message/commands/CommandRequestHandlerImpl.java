package com.github.yehortpk.telegrambot.handlers.message.commands;

import com.github.yehortpk.telegrambot.models.UserRequestDTO;
import com.github.yehortpk.telegrambot.handlers.RequestHandlerImpl;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link CommandRequestHandler}. Reduces boilerplate code in command handlers <code>isApplicable</code>
 * method to simply specify the command in <code>getCommand</code> method. Also decorate handlers
 * <code>handleRequest</code> method to perform additional actions in every command handler.
 *
 * <p>The command is the message's first word. In a group, Telegram may append the bot's name to
 * it ({@code /link@SomeBot}), which is ignored; what follows the command is its argument.
 */
@Component
public abstract class CommandRequestHandlerImpl extends RequestHandlerImpl implements CommandRequestHandler {
    private static String[] splitMessageText(UserRequestDTO userRequest) {
        return userRequest.getUpdate().getMessage().getText().strip().split("\\s+");
    }

    private static String stripBotName(String command) {
        int botNameStart = command.indexOf('@');
        return botNameStart < 0 ? command : command.substring(0, botNameStart);
    }

    @Override
    public boolean isApplicable(UserRequestDTO userRequest) {
        String text = userRequest.getUpdate().getMessage().getText();
        return text != null && !text.isBlank()
                && stripBotName(splitMessageText(userRequest)[0]).equals(getCommand());
    }

    /**
     * The first word after the command, or null if there is none.
     */
    protected String findCommandArgument(UserRequestDTO userRequest) {
        String[] parts = splitMessageText(userRequest);
        return parts.length < 2 ? null : parts[1];
    }

    /**
     * Specify the command for <code>isApplicable</code> method filtering
     *
     * @return command
     */
    public abstract String getCommand();
}
