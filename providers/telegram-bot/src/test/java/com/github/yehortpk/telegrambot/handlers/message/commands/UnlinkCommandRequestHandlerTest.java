package com.github.yehortpk.telegrambot.handlers.message.commands;

import com.github.yehortpk.telegrambot.models.UserRequestDTO;
import com.github.yehortpk.telegrambot.services.ConnectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UnlinkCommandRequestHandlerTest {
    private static final long CHAT_ID = -1001234567890L;

    @Mock
    private ConnectorService connectorService;
    @InjectMocks
    private UnlinkCommandRequestHandler handler;

    private UserRequestDTO createRequest(String text) {
        Message message = new Message();
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);

        return new UserRequestDTO(CHAT_ID, update);
    }

    @Test
    void IsApplicable_UnlinkCommand_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/unlink")));
    }

    @Test
    void IsApplicable_LinkCommand_ShouldReturnFalse() {
        assertFalse(handler.isApplicable(createRequest("/link code")));
    }

    @Test
    void HandleRequest_LinkedChat_ShouldUnlinkIt() {
        SendMessage reply = handler.handleRequest(createRequest("/unlink"));

        verify(connectorService).unlinkTelegramConnection(CHAT_ID);
        assertTrue(reply.getText().contains("will receive no notifications"));
    }

    @Test
    void HandleRequest_RouterUnreachable_ShouldAskToRetry() {
        doThrow(new ResourceAccessException("connection refused"))
                .when(connectorService).unlinkTelegramConnection(CHAT_ID);

        SendMessage reply = handler.handleRequest(createRequest("/unlink"));

        assertTrue(reply.getText().contains("try again later"));
    }
}
