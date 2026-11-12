package com.github.yehortpk.subscriberbot.handlers;

import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Main class that responsible for handling {@link Update} request with the list of handlers
 */
public class Dispatcher {
    private final List<RequestHandler> handlers;
    private final long chatId;

    public Dispatcher(List<RequestHandler> handlers, long chatId) {
        this.handlers = handlers;
        this.chatId = chatId;
    }

    /**
     * Check if any handler is applicable for the {@link Update} request. If so, handle it.
     *
     * @param userRequest {@link Update} user request
     */
    public void dispatch(Update userRequest) {
        // Wrap the request and the chat it came from into one DTO object
        UserRequestDTO userRequestDTO = new UserRequestDTO(chatId, userRequest);

        for (RequestHandler handler : handlers) {
            if (handler.isApplicable(userRequestDTO)) {
                handler.handle(userRequestDTO);
                break;
            }
        }
    }
}
