package com.github.yehortpk.telegrambot.handlers;

import com.github.yehortpk.telegrambot.models.UserRequestDTO;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Core handler interface that represents main methods for handlers dispatching.
 */
public interface RequestHandler {
    /**
     * Checks if the handler can be applicable to the current {@link Update} request
     *
     * @param userRequest {@link UserRequestDTO} update wrapper with current user instance
     * @return <code>True</code> if handler applicable
     */
    boolean isApplicable(UserRequestDTO userRequest);

    /**
     * Handle the request
     *
     * @param userRequest {@link UserRequestDTO} user request
     */
    void handle(UserRequestDTO userRequest);
}
