package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartCommandRequestHandlerTest {
    private static final long CHAT_ID = 473123822L;

    private final StartCommandRequestHandler handler = new StartCommandRequestHandler();

    private UserRequestDTO createRequest(String text) {
        Message message = new Message();
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);

        return new UserRequestDTO(CHAT_ID, update);
    }

    @Test
    void IsApplicable_StartCommandWithDeepLinkPayload_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/start payload")));
    }

    @Test
    void HandleRequest_AnyChat_ShouldExplainLinkingFromTheAccountPage() {
        SendMessage reply = handler.handleRequest(createRequest("/start"));

        assertEquals(String.valueOf(CHAT_ID), reply.getChatId());
        assertTrue(reply.getText().contains("account page"));
        assertTrue(reply.getText().contains("/link your-code"));
        // The filter menu is gone: filters are managed in the web app
        assertNull(reply.getReplyMarkup());
    }
}
