package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.models.ConnectorStatusDTO;
import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import com.github.yehortpk.subscriberbot.services.ConnectorService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusCommandRequestHandlerTest {
    private static final long CHAT_ID = 473123822L;

    @Mock
    private ConnectorService connectorService;
    @InjectMocks
    private StatusCommandRequestHandler handler;

    private UserRequestDTO createRequest(String text) {
        Message message = new Message();
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);

        return new UserRequestDTO(CHAT_ID, update);
    }

    @Test
    void IsApplicable_StatusCommand_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/status")));
    }

    @Test
    void IsApplicable_OtherCommand_ShouldReturnFalse() {
        assertFalse(handler.isApplicable(createRequest("/start")));
    }

    @Test
    void HandleRequest_LinkedChat_ShouldReportTheAccountAndFilterCounts() {
        when(connectorService.findTelegramStatus(CHAT_ID))
                .thenReturn(new ConnectorStatusDTO(true, "me@example.com", 3, 2));

        SendMessage reply = handler.handleRequest(createRequest("/status"));

        assertTrue(reply.getText().contains("<b>me@example.com</b>"));
        assertTrue(reply.getText().contains("2 of the account's 3 filters deliver here"));
    }

    @Test
    void HandleRequest_UnlinkedChat_ShouldExplainHowToLink() {
        when(connectorService.findTelegramStatus(CHAT_ID)).thenReturn(new ConnectorStatusDTO(false, null, 0, 0));

        SendMessage reply = handler.handleRequest(createRequest("/status"));

        assertTrue(reply.getText().contains("not linked"));
    }

    @Test
    void HandleRequest_EmailWithMarkup_ShouldEscapeIt() {
        // Replies are parsed as HTML; an unescaped address could break or inject markup
        when(connectorService.findTelegramStatus(CHAT_ID))
                .thenReturn(new ConnectorStatusDTO(true, "a&b@example.com", 1, 1));

        SendMessage reply = handler.handleRequest(createRequest("/status"));

        assertTrue(reply.getText().contains("a&amp;b@example.com"));
    }

    @Test
    void HandleRequest_RouterUnreachable_ShouldAskToRetry() {
        when(connectorService.findTelegramStatus(CHAT_ID)).thenThrow(new ResourceAccessException("connection refused"));

        SendMessage reply = handler.handleRequest(createRequest("/status"));

        assertTrue(reply.getText().contains("try again later"));
    }
}
